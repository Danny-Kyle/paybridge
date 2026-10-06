package com.academy.paybridge.customer.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.NotBlank;

public record OnboardCustomerRequest(
        @NotBlank(message = "customer detail is required") String c,
        @Email(message = "Invalid email address") @NotBlank(message = "Email is required") String email,
        @Pattern(regexp = "") phone;
        ) {
}
