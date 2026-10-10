package com.academy.paybridge.transfer.client.paystack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record TransferData(@JsonProperty("transfer_code") String transferCode, String status, String reference) {}
