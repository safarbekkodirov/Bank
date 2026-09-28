package com.minibank.exception;

/** 404 Not Found — изделген нерсе жок. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
