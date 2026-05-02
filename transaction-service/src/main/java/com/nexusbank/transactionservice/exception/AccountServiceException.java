package com.nexusbank.transactionservice.exception;

import org.springframework.http.HttpStatus;

public class AccountServiceException extends RuntimeException {

    private final boolean retryable;
    private final HttpStatus suggestedStatus;

    public AccountServiceException(String message, boolean retryable, HttpStatus suggestedStatus) {
        super(message);
        this.retryable = retryable;
        this.suggestedStatus = suggestedStatus;
    }

    public AccountServiceException(String message, Throwable cause, boolean retryable, HttpStatus suggestedStatus) {
        super(message, cause);
        this.retryable = retryable;
        this.suggestedStatus = suggestedStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public HttpStatus getSuggestedStatus() {
        return suggestedStatus;
    }
}
