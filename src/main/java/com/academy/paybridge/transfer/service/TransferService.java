package com.academy.paybridge.transfer.service;

import com.academy.paybridge.account.api.AccountApi;
import com.academy.paybridge.account.api.AccountKind;
import com.academy.paybridge.account.api.AccountSummary;
import com.academy.paybridge.compliance.api.ComplianceApi;
import com.academy.paybridge.compliance.api.Decision;
import com.academy.paybridge.compliance.api.ScreeningRequest;
import com.academy.paybridge.compliance.api.ScreeningResult;
import com.academy.paybridge.customer.api.CustomerApi;
import com.academy.paybridge.customer.api.CustomerSummary;
import com.academy.paybridge.ledger.api.*;
import com.academy.paybridge.shared.exception.BusinessRuleException;
import com.academy.paybridge.shared.exception.ResourceNotFoundException;
import com.academy.paybridge.shared.money.Money;
import com.academy.paybridge.transfer.api.TransferCompleted;
import com.academy.paybridge.transfer.api.TransferFailed;
import com.academy.paybridge.transfer.client.*;
import com.academy.paybridge.transfer.domain.*;
import com.academy.paybridge.transfer.repository.TransferRecipientRepository;
import com.academy.paybridge.transfer.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferRepository transfers;
    private final TransferRecipientRepository recipients;
    private final CustomerApi customerApi;
    private final AccountApi accountApi;
    private final LedgerApi ledgerApi;
    private final ComplianceApi complianceApi;
    private final TransferSnapshotAssembler assembler;
    private final RoutingPolicy routingPolicy;
    private final TransferGateway gateway;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public TransferService(TransferRepository transfers, TransferRecipientRepository recipients,
                           CustomerApi customerApi, AccountApi accountApi, LedgerApi ledgerApi,
                           ComplianceApi complianceApi, TransferSnapshotAssembler assembler,
                           RoutingPolicy routingPolicy, TransferGateway gateway,
                           ApplicationEventPublisher events, TransactionTemplate tx, Clock clock) {
        this.transfers = transfers; this.recipients = recipients; this.customerApi = customerApi;
        this.accountApi = accountApi; this.ledgerApi = ledgerApi; this.complianceApi = complianceApi;
        this.assembler = assembler; this.routingPolicy = routingPolicy; this.gateway = gateway;
        this.events = events; this.tx = tx; this.clock = clock;
    }

    // NOT @Transactional on purpose: we must not hold DB locks while calling the gateway over HTTP.
    public Transfer initiate(InitiateTransferCommand cmd) {
        var existing = transfers.findByIdempotencyKey(cmd.idempotencyKey());
        if (existing.isPresent()) return existing.get();                         // replay-safe

        if (cmd.senderCustomerId().equals(cmd.recipientCustomerId()))
            throw new BusinessRuleException("SELF_TRANSFER", "You cannot transfer to yourself.");
        if (!cmd.amount().isPositive())
            throw new BusinessRuleException("INVALID_AMOUNT", "Amount must be positive.");

        CustomerSummary sender = customerApi.getCustomer(cmd.senderCustomerId());
        AccountSummary senderAccount = accountApi.findPrimaryAccount(sender.id())
                .filter(a -> a.kind() == AccountKind.INTERNAL)
                .orElseThrow(() -> new BusinessRuleException("SENDER_HAS_NO_ACCOUNT", "Sender has no PayBridge account."));
        if (senderAccount.currency() != cmd.amount().currency())
            throw new BusinessRuleException("CURRENCY_MISMATCH", "Sender account currency is " + senderAccount.currency());

        TransferSnapshot snapshot = assembler.assemble(cmd.recipientCustomerId());
        RoutingDecision decision = routingPolicy.decide(snapshot);
        AccountSummary destination = decision.destination().account().orElseThrow();

        String reference = newReference();
        ScreeningResult screening = complianceApi.screen(new ScreeningRequest(
                reference, sender.id(), sender.kycVerified(), destination.accountName(), cmd.amount()));

        RoutingOutcome outcome = switch (decision) {
            case RoutingDecision.Direct d -> RoutingOutcome.DIRECT;
            case RoutingDecision.RedirectToNextOfKin r -> RoutingOutcome.REDIRECTED_TO_NEXT_OF_KIN;
        };

        Transfer transfer = Transfer.create(reference, cmd.idempotencyKey(),
                sender.id(), senderAccount.accountId(), cmd.recipientCustomerId(),
                decision.destination().customerId(), destination.accountId(),
                destination.accountNumber(), destination.bankCode(),
                cmd.amount(), outcome, decision.reason(), cmd.narration());

        if (screening.decision() != Decision.ALLOW) {
            transfer.reject("COMPLIANCE_" + screening.decision() + ": " + String.join("; ", screening.reasons()));
            return transfers.save(transfer);
        }

        UUID id = transfers.save(transfer).getId();
        try {
            return destination.kind() == AccountKind.INTERNAL ? settleInternal(id) : startExternal(id, destination);
        } catch (BusinessRuleException e) {                                       // e.g. INSUFFICIENT_FUNDS
            failPending(id, e.getMessage());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Transfer getOrThrow(UUID id) {
        return transfers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Transfer", id));
    }

    // ---- called by the webhook ------------------------------------------------------------

    @Transactional
    public void onGatewaySuccess(String reference) {
        transfers.findByReference(reference).ifPresentOrElse(this::completeExternal,
                () -> log.warn("Webhook for unknown transfer reference {}", reference));
    }

    @Transactional
    public void onGatewayFailure(String reference, String reason, boolean reversed) {
        transfers.findByReference(reference).ifPresentOrElse(t -> refundExternal(t, reason, reversed),
                () -> log.warn("Webhook for unknown transfer reference {}", reference));
    }

    // ---- internal flows -------------------------------------------------------------------

    private Transfer settleInternal(UUID id) {
        return update(id, t -> {
            moveFunds(t.getSenderAccountId(), t.getDestinationAccountId(), t.amount());
            ledgerApi.post(new PostJournalCommand(t.getReference(), JournalType.INTERNAL_TRANSFER,
                    "Internal transfer " + t.getReference(), List.of(
                    EntryLine.debit(AccountRefs.customer(t.getSenderAccountId()), t.amount()),
                    EntryLine.credit(AccountRefs.customer(t.getDestinationAccountId()), t.amount()))));
            t.markCompleted();
            publishCompleted(t);
        });
    }

    private Transfer startExternal(UUID id, AccountSummary destination) {
        // Transaction 1: take the money out of the sender's account and park it in clearing.
        Transfer started = update(id, t -> {
            accountApi.debit(t.getSenderAccountId(), t.amount());
            ledgerApi.post(new PostJournalCommand(t.getReference() + "-start", JournalType.PAYOUT_STARTED,
                    "Payout started " + t.getReference(), List.of(
                    EntryLine.debit(AccountRefs.customer(t.getSenderAccountId()), t.amount()),
                    EntryLine.credit(AccountRefs.PAYSTACK_CLEARING, t.amount()))));
            t.markProcessing();
        });

        // No transaction open from here: HTTP call to the gateway.
        try {
            String recipientCode = recipientCode(destination);
            GatewayTransferResult result = gateway.initiateTransfer(new GatewayTransferRequest(
                    started.getReference(), started.amount(), recipientCode,
                    started.getNarration() != null ? started.getNarration() : "PayBridge transfer"));

            return switch (result.status()) {
                case SUCCESS -> update(id, t -> { t.recordGatewayCode(result.transferCode()); completeExternal(t); });
                case PENDING -> update(id, t -> t.recordGatewayCode(result.transferCode()));   // webhook will finish it
                case FAILED, OTP_REQUIRED -> update(id, t -> refundExternal(t, "GATEWAY_" + result.status(), false));
            };
        } catch (GatewayUnavailableException e) {
            log.warn("Gateway timeout for {}; leaving PROCESSING until webhook/verify", started.getReference(), e);
            return transfers.findById(id).orElseThrow();                          // outcome UNKNOWN: do NOT refund
        } catch (GatewayException e) {
            return update(id, t -> refundExternal(t, e.getCode() + ": " + e.getMessage(), false));
        }
    }

    private void completeExternal(Transfer t) {
        if (t.getStatus() == TransferStatus.COMPLETED) return;
        if (t.getStatus() != TransferStatus.PROCESSING && t.getStatus() != TransferStatus.PENDING) {
            log.error("Success event for transfer {} in state {}; ignoring", t.getReference(), t.getStatus());
            return;
        }
        ledgerApi.post(new PostJournalCommand(t.getReference() + "-settle", JournalType.PAYOUT_SETTLED,
                "Payout settled " + t.getReference(), List.of(
                EntryLine.debit(AccountRefs.PAYSTACK_CLEARING, t.amount()),
                EntryLine.credit(AccountRefs.PAYSTACK_FLOAT, t.amount()))));
        t.markCompleted();
        publishCompleted(t);
    }

    private void refundExternal(Transfer t, String reason, boolean reversed) {
        TransferStatus s = t.getStatus();
        if (s == TransferStatus.FAILED || s == TransferStatus.REVERSED || s == TransferStatus.REJECTED) return;  // idempotent

        if (s == TransferStatus.COMPLETED) {                                       // reversal after success: undo settlement first
            ledgerApi.post(new PostJournalCommand(t.getReference() + "-unsettle", JournalType.PAYOUT_UNSETTLED,
                    "Payout unsettled " + t.getReference(), List.of(
                    EntryLine.debit(AccountRefs.PAYSTACK_FLOAT, t.amount()),
                    EntryLine.credit(AccountRefs.PAYSTACK_CLEARING, t.amount()))));
        }
        accountApi.credit(t.getSenderAccountId(), t.amount());
        ledgerApi.post(new PostJournalCommand(t.getReference() + "-refund", JournalType.PAYOUT_REFUNDED,
                "Payout refunded " + t.getReference(), List.of(
                EntryLine.debit(AccountRefs.PAYSTACK_CLEARING, t.amount()),
                EntryLine.credit(AccountRefs.customer(t.getSenderAccountId()), t.amount()))));
        if (reversed) t.markReversed(reason); else t.markFailed(reason);
        events.publishEvent(new TransferFailed(t.getId(), t.getSenderCustomerId(), reason));
    }

    /** Lock both accounts in a fixed (id) order so two opposite transfers cannot deadlock. */
    private void moveFunds(UUID from, UUID to, Money amount) {
        if (from.compareTo(to) < 0) { accountApi.debit(from, amount); accountApi.credit(to, amount); }
        else                        { accountApi.credit(to, amount);  accountApi.debit(from, amount); }
    }

    private String recipientCode(AccountSummary d) {
        return recipients.findByBankCodeAndAccountNumber(d.bankCode(), d.accountNumber())
                .map(TransferRecipient::getRecipientCode)
                .orElseGet(() -> {
                    String code = gateway.createRecipient(d.accountName(), d.bankCode(), d.accountNumber());
                    recipients.save(TransferRecipient.of(d.bankCode(), d.accountNumber(), code, clock.instant()));
                    return code;
                });
    }

    private Transfer update(UUID id, Consumer<Transfer> action) {
        return tx.execute(status -> {
            Transfer t = transfers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Transfer", id));
            action.accept(t);
            return t;
        });
    }

    private void failPending(UUID id, String reason) {
        tx.executeWithoutResult(s -> transfers.findById(id).ifPresent(t -> {
            if (t.getStatus() == TransferStatus.PENDING) t.markFailed(reason);
        }));
    }

    private void publishCompleted(Transfer t) {
        events.publishEvent(new TransferCompleted(t.getId(), t.getSenderCustomerId(), t.getDestinationCustomerId(),
                t.getRequestedRecipientCustomerId(), t.getRoutingOutcome() == RoutingOutcome.REDIRECTED_TO_NEXT_OF_KIN,
                t.amount()));
    }

    private static String newReference() {
        return "pb-" + UUID.randomUUID().toString().replace("-", "");               // 35 chars, lowercase
    }
}