package com.restio.shared.error;

/**
 * Stable, machine-readable error identifier returned in the {@code code} field of every problem
 * response. Each module prefixes its own codes with the module name.
 */
public interface ErrorCode {

    String code();

    /** Generic codes owned by {@code shared}; modules declare their own enums. */
    enum Common implements ErrorCode {
        VALIDATION_FAILED,
        NOT_FOUND,
        FORBIDDEN,
        UNAUTHORIZED,
        CONCURRENT_MODIFICATION,
        INTERNAL_ERROR;

        @Override
        public String code() {
            return name();
        }
    }
}
