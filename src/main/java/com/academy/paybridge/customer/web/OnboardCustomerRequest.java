package com.academy.paybridge.customer.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.NotBlank;

public record OnboardCustomerRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Email(message = "Invalid email address") @NotBlank(message = "Email is required") String email,
        @NotBlank @Pattern(regexp = "^(\\+234|0)[789][01]\\d{8}$", message = "Invalid Nigerian phone number") String phoneNumber) {}


