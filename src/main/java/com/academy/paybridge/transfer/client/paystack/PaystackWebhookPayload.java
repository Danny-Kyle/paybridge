package com.academy.paybridge.transfer.client.paystack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaystackWebhookPayload(String event, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String reference, String status, String reason,
                       @JsonProperty("transfer_code") String transferCode) {}
}