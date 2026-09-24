package com.restio.shared.error;

/**
 * The caller could not be authenticated: wrong credentials, a locked account or an unusable token.
 * Maps to HTTP 401 with the module's own code.
 */
public class UnauthorizedException extends RuntimeException {

    private final ErrorCode errorCode;

    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
