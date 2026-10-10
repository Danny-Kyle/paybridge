package com.academy.paybridge.compliance.api;

import com.academy.paybridge.shared.money.Money;
import java.util.UUID;

public record ScreeningRequest(String transferReference, UUID senderCustomerId, boolean senderKycVerified,
                               String recipientName, Money amount) {}