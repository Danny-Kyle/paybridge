package com.academy.paybridge.transfer.web;

import com.academy.paybridge.shared.money.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record InitiateTransferRequest(@NotNull UUID senderCustomerId, @NotNull UUID recipientCustomerId,
                                      @Positive long amountMinor, @NotNull Currency currency,
                                      @Size(max = 255) String narration) {}