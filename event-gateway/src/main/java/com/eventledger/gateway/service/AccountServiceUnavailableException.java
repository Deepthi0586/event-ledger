package com.eventledger.gateway.service;

public class AccountServiceUnavailableException
        extends RuntimeException {

    public AccountServiceUnavailableException(String message) {
        super(message);
    }

    public AccountServiceUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}