package com.academy.paybridge.transfer.api;

import java.util.UUID;

public record TransferFailed(UUID transferId, UUID senderCustomerId, String reason) {}