package com.academy.paybridge.customer.api;

import java.util.UUID;

public record CustomerSummary(UUID id, String fullName, String email, boolean KycVerified) {
}
