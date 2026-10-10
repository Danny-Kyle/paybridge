package com.academy.paybridge.transfer.client;

import com.academy.paybridge.shared.money.Money;

public record GatewayTransferRequest(String reference, Money amount, String recipientCode, String narration) {}

