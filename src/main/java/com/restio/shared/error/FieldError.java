package com.restio.shared.error;

/** One invalid field, as listed in the {@code errors} array of a validation problem response. */
public record FieldError(String field, String message) {}
