package com.restio.auth.api.dto;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response bodies of {@code /api/v1/auth}. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record PinLoginRequest(
            @NotNull Long deviceId,
            @NotBlank String deviceToken,
            @NotBlank @Pattern(regexp = "\\d{4,6}") String pin) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    public record ChangePinRequest(
            @NotBlank String currentSecret,
            @NotBlank @Pattern(regexp = "\\d{4,6}") String newPin) {}

    /** {@code expiresIn} is the access token lifetime in seconds. */
    public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {}

    public record MeResponse(
            Long id,
            String email,
            String displayName,
            Set<Long> restaurantIds,
            Set<String> roles,
            Set<String> permissions,
            Long deviceId) {}
}
