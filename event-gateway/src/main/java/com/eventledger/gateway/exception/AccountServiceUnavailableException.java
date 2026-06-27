package com.eventledger.gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.SERVICE_UNAVAILABLE)
public class AccountServiceUnavailableException extends RuntimeException {
    public AccountServiceUnavailableException(String message) {
        super(message);
    }

    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
