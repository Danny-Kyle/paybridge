package com.academy.paybridge.shared.exception;

public class BusinessRuleException extends PaybridgeException {
    public BusinessRuleException(String message, String code) {
        super(message, code);
    }
}
