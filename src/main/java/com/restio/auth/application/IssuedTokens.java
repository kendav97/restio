package com.restio.auth.application;

import java.time.Duration;

/** Tokens handed to a client after a login. {@code refreshToken} is null for PIN sessions. */
public record IssuedTokens(String accessToken, String refreshToken, Duration expiresIn) {}
