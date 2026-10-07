package com.academy.paybridge.shared.exception;


public class ResourceNotFoundException extends PaybridgeException{
    public ResourceNotFoundException(String resource, Object id) {
        super("RESOURCE NOT FOUND", resource + "not found: " + id);
    }
}

