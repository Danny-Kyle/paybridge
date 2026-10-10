package com.academy.paybridge.transfer.client.paystack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record PaystackEnvelope<T>(boolean status, String message, T data) {}
