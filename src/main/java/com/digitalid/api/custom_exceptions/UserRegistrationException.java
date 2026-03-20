package com.digitalid.api.custom_exceptions;

public class UserRegistrationException extends RuntimeException {

    public UserRegistrationException(String message) {
        super(message);
    }
}
