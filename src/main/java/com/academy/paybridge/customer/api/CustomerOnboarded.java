package com.academy.paybridge.customer.api;

import java.util.UUID;

public record CustomerOnboarded(UUID customerId, String fullName, String email) {
}
