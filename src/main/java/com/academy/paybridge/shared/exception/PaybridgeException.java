package com.academy.paybridge.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;


public abstract class PaybridgeException extends RuntimeException {

    @ExceptionHandler(MethodArgumentNotValidException.class)

    @RestControllerAdvice
    public class GlobalExceptionHandler{
        ProblemDetail handleValidation(MethodArgumentNotValidException e){};
        ProblemDetail handleNotFound(ResourceNotFoundException e){};
        ProblemDetail handleBusinessValue (BusinessRuleException e){};
        ProblemDetail handleUnexpected(Exception){}
    }


    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
//        StringBuilder message = new StringBuilder();
//        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
//
//        }
String detailMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();

        ProblemDetail problem =ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detailMessage);
        problem.setTitle("Bad Request");

        return problem.toString();
    }
}
