package com.academy.paybridge.transfer.client;

import com.academy.paybridge.shared.exception.PaybridgeException;

public class GatewayUnavailableException extends PaybridgeException { // timeout: outcome UNKNOWN
    public GatewayUnavailableException(String message) { super("GATEWAY_UNAVAILABLE", message); }
}
