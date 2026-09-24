package com.restio.auth.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.restio.auth.api.dto.AuthDtos.ChangePasswordRequest;
import com.restio.auth.api.dto.AuthDtos.ChangePinRequest;
import com.restio.auth.api.dto.AuthDtos.LoginRequest;
import com.restio.auth.api.dto.AuthDtos.MeResponse;
import com.restio.auth.api.dto.AuthDtos.PinLoginRequest;
import com.restio.auth.api.dto.AuthDtos.RefreshRequest;
import com.restio.auth.api.dto.AuthDtos.TokenResponse;
import com.restio.auth.api.dto.DeviceDtos.DeviceLoginRequest;
import com.restio.auth.application.AuthService;
import com.restio.auth.application.IssuedTokens;
import com.restio.auth.domain.User;
import com.restio.shared.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "auth")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Log in with email and password")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(authService.login(request.email(), request.password()));
    }

    @Operation(summary = "Log in with a PIN from a registered device (shift session, no refresh)")
    @PostMapping("/pin-login")
    public TokenResponse pinLogin(@Valid @RequestBody PinLoginRequest request) {
        return toResponse(
                authService.pinLogin(request.deviceId(), request.deviceToken(), request.pin()));
    }

    @Operation(summary = "Session of a linked device itself, for synchronisation (no user)")
    @PostMapping("/device-login")
    public TokenResponse deviceLogin(@Valid @RequestBody DeviceLoginRequest request) {
        return toResponse(authService.deviceLogin(request.deviceId(), request.deviceToken()));
    }

    @Operation(summary = "Exchange a refresh token for a new token pair")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return toResponse(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Revoke a refresh token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Current user, restaurants and permissions")
    @GetMapping("/me")
    @PreAuthorize("!hasRole('DEVICE')")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = authService.currentUser(principal.userId());
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                principal.restaurantIds(),
                principal.roles(),
                principal.permissions(),
                principal.deviceId());
    }

    @Operation(summary = "Change the password; closes every other session")
    @PostMapping("/change-password")
    @PreAuthorize("!hasRole('DEVICE')")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(
                principal.userId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Change the PIN, proving identity with the current PIN or password")
    @PostMapping("/change-pin")
    @PreAuthorize("!hasRole('DEVICE')")
    public ResponseEntity<Void> changePin(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePinRequest request) {
        authService.changePin(principal.userId(), request.currentSecret(), request.newPin());
        return ResponseEntity.noContent().build();
    }

    private static TokenResponse toResponse(IssuedTokens tokens) {
        return new TokenResponse(
                tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn().toSeconds());
    }
}
