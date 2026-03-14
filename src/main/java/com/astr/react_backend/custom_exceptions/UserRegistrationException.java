package com.astr.react_backend.custom_exceptions;

public class UserRegistrationException extends RuntimeException {

    public UserRegistrationException(String message) {
        super(message);
    }
}
