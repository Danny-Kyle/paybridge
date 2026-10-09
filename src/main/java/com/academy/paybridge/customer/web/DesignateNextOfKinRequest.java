package com.academy.paybridge.customer.web;

import com.academy.paybridge.customer.domain.Relationship;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DesignateNextOfKinRequest(
        @NotNull
        UUID nextOfKinCustomerId,

        @NotNull
        Relationship relationship
) {
}
