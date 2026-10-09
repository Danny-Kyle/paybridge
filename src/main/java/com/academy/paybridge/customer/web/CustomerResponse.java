package com.academy.paybridge.customer.web;

import com.academy.paybridge.customer.domain.Customer;
import com.academy.paybridge.customer.domain.KycStatus;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(UUID id, String firstName, String lastName, String email,
                               String phoneNumber, KycStatus kycStatus, Instant createdAt) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(),
                c.getPhoneNumber(), c.getKycStatus(), c.getCreatedAt());
    }
}
