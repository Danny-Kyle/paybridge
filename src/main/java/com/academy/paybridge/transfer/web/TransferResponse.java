package com.academy.paybridge.transfer.web;

import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.transfer.domain.RoutingOutcome;
import com.academy.paybridge.transfer.domain.Transfer;
import com.academy.paybridge.transfer.domain.TransferStatus;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID id, String reference, TransferStatus status, RoutingOutcome routingOutcome,
                               String routingReason, UUID requestedRecipientCustomerId, UUID destinationCustomerId,
                               long amountMinor, Currency currency, String failureReason, Instant createdAt) {
    static TransferResponse from(Transfer t) {
        return new TransferResponse(t.getId(), t.getReference(), t.getStatus(), t.getRoutingOutcome(),
                t.getRoutingReason(), t.getRequestedRecipientCustomerId(), t.getDestinationCustomerId(),
                t.amount().minorUnits(), t.amount().currency(), t.getFailureReason(), t.getCreatedAt());
    }
}