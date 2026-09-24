package com.restio.shared.error;

/**
 * A domain rule was violated: the request was well formed but the current state does not allow it
 * (an order already closed, a table that is not free). Maps to HTTP 409.
 */
public class BusinessRuleException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
