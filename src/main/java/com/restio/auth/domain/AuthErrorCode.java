package com.restio.auth.domain;

import com.restio.shared.error.ErrorCode;

public enum AuthErrorCode implements ErrorCode {
    AUTH_INVALID_CREDENTIALS,
    AUTH_ACCOUNT_LOCKED,
    AUTH_USER_DISABLED,
    AUTH_INVALID_REFRESH_TOKEN,
    AUTH_INVALID_DEVICE,
    AUTH_PIN_IN_USE,
    AUTH_PIN_INVALID_FORMAT,
    AUTH_INVALID_PAIRING_CODE;

    @Override
    public String code() {
        return name();
    }
}
