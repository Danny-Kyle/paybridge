package com.academy.paybridge.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;


public abstract class PaybridgeException extends RuntimeException {

    private final String code;

    protected PaybridgeException(String code, String message) {
        super(message);
        this.code = code;
    };

    public String getCode() {
        return code;
    };
}
