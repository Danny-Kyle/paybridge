package com.academy.paybridge.transfer.client.paystack;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("paybridge.paystack")
public record PaystackProperties(String baseUrl, String secretKey) {}