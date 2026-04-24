package com.nexusbank.accountservice.exception;

public class AccountOperationException extends RuntimeException {

    public AccountOperationException(String message) {
        super(message);
    }
}
