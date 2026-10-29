package com.academy.paybridge.transfer.web;

import com.academy.paybridge.shared.exception.BusinessRuleException;
import com.academy.paybridge.shared.money.Money;
import com.academy.paybridge.transfer.service.InitiateTransferCommand;
import com.academy.paybridge.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Initiate transfers; routing to next of kin and external payouts are applied automatically")
class TransferController {

    private final TransferService service;
    private final com.academy.paybridge.customer.api.CustomerApi customers;

    TransferController(TransferService service, com.academy.paybridge.customer.api.CustomerApi customers) {
        this.service=service; this.customers=customers;
    }

    @Operation(summary = "Initiate a transfer",
            description = """
                    Routing: if the recipient's PayBridge balance is at or above the configured threshold and they
                    have a next of kin with an account, the money goes to the next of kin instead
                    (`routingOutcome` = REDIRECTED_TO_NEXT_OF_KIN). External destinations are paid out via the gateway.

                    Send the same `Idempotency-Key` to safely retry: you get the original transfer back.""")
    @ApiResponse(responseCode = "201", description = "Completed immediately (internal transfer, or payout confirmed)")
    @ApiResponse(responseCode = "202", description = "Accepted; payout still processing, final status arrives by webhook")
    @ApiResponse(responseCode = "422", description = "Rejected or failed; the body shows the status and failureReason")
    @PostMapping
    ResponseEntity<TransferResponse> initiate(
            @Parameter(description = "Unique per logical transfer, 1 to 64 characters", example = "demo-001", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InitiateTransferRequest req) {

        if (idempotencyKey.isBlank() || idempotencyKey.length() > 64)
            throw new BusinessRuleException("BAD_IDEMPOTENCY_KEY", "Idempotency-Key must be 1 to 64 characters.");

        customers.requireOwner(req.senderCustomerId());
        var transfer = service.initiate(new InitiateTransferCommand(idempotencyKey, req.senderCustomerId(),
                req.recipientCustomerId(), new Money(req.amountMinor(), req.currency()), req.narration()));
        var body = TransferResponse.from(transfer);

        return switch (transfer.getStatus()) {
            case COMPLETED -> ResponseEntity.created(URI.create("/api/v1/transfers/" + transfer.getId())).body(body);
            case PENDING, PROCESSING -> ResponseEntity.accepted()
                    .location(URI.create("/api/v1/transfers/" + transfer.getId())).body(body);
            default -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
        };
    }

    @Operation(summary = "Get a transfer and its current status")
    @GetMapping("/{id}")
    TransferResponse get(@PathVariable UUID id) {
        var t=service.getOrThrow(id); customers.requireOwner(t.getSenderCustomerId());
        return TransferResponse.from(t);
    }
}