package com.academy.paybridge.transfer.client;

import com.academy.paybridge.shared.exception.PaybridgeException;

public class GatewayException extends PaybridgeException {          // 4xx or business rejection from the gateway
    public GatewayException(String code, String message) { super(code, message); }
}
