package com.restio.shared.error;

/** The requested resource does not exist, or is not visible to the caller. Maps to HTTP 404. */
public class NotFoundException extends RuntimeException {

    private final ErrorCode errorCode;

    public NotFoundException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public NotFoundException(String resource, Object id) {
        this(ErrorCode.Common.NOT_FOUND, resource + " " + id + " does not exist");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
