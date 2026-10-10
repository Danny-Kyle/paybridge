package com.academy.paybridge.transfer.web;

import com.academy.paybridge.shared.exception.BusinessRuleException;
import com.academy.paybridge.shared.money.Money;
import com.academy.paybridge.transfer.service.InitiateTransferCommand;
import com.academy.paybridge.transfer.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
class TransferController {

    private final TransferService service;

    TransferController(TransferService service) { this.service = service; }

    @PostMapping
    ResponseEntity<TransferResponse> initiate(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                              @Valid @RequestBody InitiateTransferRequest req) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 64)
            throw new BusinessRuleException("BAD_IDEMPOTENCY_KEY", "Idempotency-Key must be 1 to 64 characters.");

        var transfer = service.initiate(new InitiateTransferCommand(idempotencyKey, req.senderCustomerId(),
                req.recipientCustomerId(), new Money(req.amountMinor(), req.currency()), req.narration()));
        var body = TransferResponse.from(transfer);

        return switch (transfer.getStatus()) {
            case COMPLETED -> ResponseEntity.created(URI.create("/api/v1/transfers/" + transfer.getId())).body(body);
            case PENDING, PROCESSING -> ResponseEntity.accepted()
                    .location(URI.create("/api/v1/transfers/" + transfer.getId())).body(body);
            default -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(body);   // REJECTED, FAILED, REVERSED
        };
    }

    @GetMapping("/{id}")
    TransferResponse get(@PathVariable UUID id) { return TransferResponse.from(service.getOrThrow(id)); }
}