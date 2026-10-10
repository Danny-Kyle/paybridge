package com.academy.paybridge.transfer.api;

import com.academy.paybridge.shared.money.Money;
import java.util.UUID;

public record TransferCompleted(UUID transferId, UUID senderCustomerId, UUID destinationCustomerId,
                                UUID originalRecipientCustomerId, boolean redirected, Money amount) {}