package com.academy.paybridge.transfer.domain;

import com.academy.paybridge.shared.audit.BaseEntity;
import com.academy.paybridge.shared.exception.BusinessRuleException;
import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "transfer")
public class Transfer extends BaseEntity {

    @Column(nullable = false, updatable = false) private String reference;
    @Column(name = "idempotency_key", nullable = false, updatable = false) private String idempotencyKey;
    @Column(name = "sender_customer_id", nullable = false, updatable = false) private UUID senderCustomerId;
    @Column(name = "sender_account_id", nullable = false, updatable = false) private UUID senderAccountId;
    @Column(name = "requested_recipient_customer_id", nullable = false, updatable = false) private UUID requestedRecipientCustomerId;
    @Column(name = "destination_customer_id", nullable = false, updatable = false) private UUID destinationCustomerId;
    @Column(name = "destination_account_id", nullable = false, updatable = false) private UUID destinationAccountId;
    @Column(name = "destination_account_number", nullable = false, updatable = false) private String destinationAccountNumber;
    @Column(name = "destination_bank_code", nullable = false, updatable = false) private String destinationBankCode;
    @Column(name = "amount_minor", nullable = false, updatable = false) private long amountMinor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private Currency currency;
    @Enumerated(EnumType.STRING) @Column(name = "routing_outcome", nullable = false, updatable = false) private RoutingOutcome routingOutcome;
    @Column(name = "routing_reason", nullable = false, updatable = false) private String routingReason;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TransferStatus status;
    @Column(name = "gateway_transfer_code") private String gatewayTransferCode;
    @Column(name = "failure_reason") private String failureReason;
    private String narration;

    protected Transfer() {}

    public static Transfer create(String reference, String idempotencyKey,
                                  UUID senderCustomerId, UUID senderAccountId,
                                  UUID requestedRecipientCustomerId,
                                  UUID destinationCustomerId, UUID destinationAccountId,
                                  String destinationAccountNumber, String destinationBankCode,
                                  Money amount, RoutingOutcome outcome, String routingReason, String narration) {
        Transfer t = new Transfer();
        t.reference = reference;
        t.idempotencyKey = idempotencyKey;
        t.senderCustomerId = senderCustomerId;
        t.senderAccountId = senderAccountId;
        t.requestedRecipientCustomerId = requestedRecipientCustomerId;
        t.destinationCustomerId = destinationCustomerId;
        t.destinationAccountId = destinationAccountId;
        t.destinationAccountNumber = destinationAccountNumber;
        t.destinationBankCode = destinationBankCode;
        t.amountMinor = amount.minorUnits();
        t.currency = amount.currency();
        t.routingOutcome = outcome;
        t.routingReason = routingReason;
        t.narration = narration;
        t.status = TransferStatus.PENDING;
        return t;
    }

    public Money amount() { return new Money(amountMinor, currency); }

    public void markProcessing() {
        require(status == TransferStatus.PENDING, "markProcessing");
        status = TransferStatus.PROCESSING;
    }

    public void recordGatewayCode(String code) { this.gatewayTransferCode = code; }

    public void markCompleted() {
        if (status == TransferStatus.COMPLETED) return;
        require(status == TransferStatus.PENDING || status == TransferStatus.PROCESSING, "markCompleted");
        status = TransferStatus.COMPLETED;
    }

    public void markFailed(String reason) {
        if (status == TransferStatus.FAILED) return;
        require(status == TransferStatus.PENDING || status == TransferStatus.PROCESSING, "markFailed");
        status = TransferStatus.FAILED;
        failureReason = reason;
    }

    public void markReversed(String reason) {
        if (status == TransferStatus.REVERSED) return;
        require(status == TransferStatus.COMPLETED || status == TransferStatus.PROCESSING, "markReversed");
        status = TransferStatus.REVERSED;
        failureReason = reason;
    }

    public void reject(String reason) {
        require(status == TransferStatus.PENDING, "reject");
        status = TransferStatus.REJECTED;
        failureReason = reason;
    }

    private void require(boolean ok, String action) {
        if (!ok) throw new BusinessRuleException("ILLEGAL_TRANSFER_STATE", "Cannot " + action + " from " + status);
    }

    public String getReference() { return reference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getSenderCustomerId() { return senderCustomerId; }
    public UUID getSenderAccountId() { return senderAccountId; }
    public UUID getRequestedRecipientCustomerId() { return requestedRecipientCustomerId; }
    public UUID getDestinationCustomerId() { return destinationCustomerId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public RoutingOutcome getRoutingOutcome() { return routingOutcome; }
    public String getRoutingReason() { return routingReason; }
    public TransferStatus getStatus() { return status; }
    public String getGatewayTransferCode() { return gatewayTransferCode; }
    public String getFailureReason() { return failureReason; }
    public String getNarration() { return narration; }
}