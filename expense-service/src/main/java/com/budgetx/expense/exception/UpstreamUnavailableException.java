package com.budgetx.expense.exception;

/** A service this one depends on (e.g. user-service) could not be reached. */
public class UpstreamUnavailableException extends RuntimeException {

    public UpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
