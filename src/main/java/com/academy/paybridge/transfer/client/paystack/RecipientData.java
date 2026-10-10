package com.academy.paybridge.transfer.client.paystack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record RecipientData(@JsonProperty("recipient_code") String recipientCode) {}
