package com.academy.paybridge.transfer.service;

import com.academy.paybridge.shared.money.Money;
import java.util.UUID;

public record InitiateTransferCommand(String idempotencyKey, UUID senderCustomerId, UUID recipientCustomerId,
                                      Money amount, String narration) {}