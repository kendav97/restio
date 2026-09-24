package com.restio.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restio.auth.domain.AuthErrorCode;
import com.restio.auth.domain.Device;
import com.restio.auth.domain.LoginLock;
import com.restio.auth.domain.RefreshToken;
import com.restio.auth.domain.User;
import com.restio.auth.infrastructure.RefreshTokenRepository;
import com.restio.auth.infrastructure.UserRepository;
import com.restio.shared.error.BusinessRuleException;
import com.restio.shared.error.NotFoundException;
import com.restio.shared.error.UnauthorizedException;
import com.restio.shared.security.AuthenticatedUser;
import com.restio.shared.security.JwtProperties;
import com.restio.shared.security.JwtService;

/**
 * Logins (password and PIN), refresh token rotation, logout and credential changes.
 *
 * <p>Failed attempts must be persisted even though the call ends in a 401, so the login methods do
 * not roll back on {@link UnauthorizedException}.
 */
@Service
@EnableConfigurationProperties(AuthProperties.class)
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Pattern PIN_FORMAT = Pattern.compile("\\d{4,6}");

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final DeviceCredentials deviceCredentials;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final LoginLock loginLock;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            DeviceCredentials deviceCredentials,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            AuthProperties authProperties,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.deviceCredentials = deviceCredentials;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.loginLock = authProperties.loginLock();
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equaliser");
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public IssuedTokens login(String email, String password) {
        Instant now = clock.instant();
        User user = users.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null || user.getPasswordHash() == null) {
            passwordEncoder.matches(password, dummyHash);
            throw invalidCredentials();
        }
        if (user.isLocked(now)) {
            throw locked();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.registerFailedAttempt(now, loginLock);
            throw invalidCredentials();
        }
        if (!user.isEnabled()) {
            throw disabled();
        }
        user.registerSuccessfulLogin(now);
        return issueSession(user, now);
    }

    /**
     * Quick login on a registered device. The PIN identifies the user among the enabled users of
     * the device's restaurant, and the session is limited to that restaurant and to one shift.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public IssuedTokens pinLogin(Long deviceId, String deviceToken, String pin) {
        Instant now = clock.instant();
        Device device =
                deviceCredentials
                        .findAuthenticated(deviceId, deviceToken)
                        .orElseThrow(AuthService::invalidDevice);
        if (device.isPinLocked(now)) {
            throw locked();
        }

        User user =
                users.findPinUsersOfRestaurant(device.getRestaurantId()).stream()
                        .filter(candidate -> passwordEncoder.matches(pin, candidate.getPinHash()))
                        .findFirst()
                        .orElse(null);
        if (user == null) {
            device.registerFailedPin(now, loginLock);
            throw invalidCredentials();
        }
        if (user.isLocked(now)) {
            throw locked();
        }

        device.registerSuccessfulPin(now);
        user.registerSuccessfulLogin(now);
        AuthenticatedUser principal =
                new AuthenticatedUser(
                        user.getId(),
                        Set.of(device.getRestaurantId()),
                        user.roleNames(),
                        permissionNames(user),
                        device.getId());
        return new IssuedTokens(
                jwtService.issue(principal, jwtProperties.pinTokenTtl()),
                null,
                jwtProperties.pinTokenTtl());
    }

    /**
     * Session of the device itself, with no user: it synchronises in the background, also while
     * nobody is logged in. Limited to the device's restaurant and without user permissions.
     */
    @Transactional
    public IssuedTokens deviceLogin(Long deviceId, String deviceToken) {
        Device device =
                deviceCredentials
                        .findAuthenticated(deviceId, deviceToken)
                        .orElseThrow(AuthService::invalidDevice);
        device.registerSeen(clock.instant());
        AuthenticatedUser principal =
                AuthenticatedUser.device(device.getId(), device.getRestaurantId());
        return new IssuedTokens(
                jwtService.issue(principal, jwtProperties.deviceTokenTtl()),
                null,
                jwtProperties.deviceTokenTtl());
    }

    /**
     * Exchanges a refresh token for a new pair. A token that was already used or revoked means it
     * leaked: every open session of the user is revoked.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public IssuedTokens refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken current =
                refreshTokens
                        .findByTokenHash(SecretTokens.hash(rawRefreshToken))
                        .orElseThrow(AuthService::invalidRefreshToken);
        if (current.isRevoked()) {
            log.warn("Reuse of revoked refresh token {}; revoking sessions", current.getId());
            revokeAll(current.getUserId(), now);
            throw invalidRefreshToken();
        }
        if (current.isExpired(now)) {
            throw invalidRefreshToken();
        }
        User user =
                users.findById(current.getUserId())
                        .filter(User::isEnabled)
                        .orElseThrow(AuthService::invalidRefreshToken);

        String raw = SecretTokens.generate();
        RefreshToken next =
                refreshTokens.save(
                        new RefreshToken(
                                user.getId(),
                                SecretTokens.hash(raw),
                                now.plus(jwtProperties.refreshTokenTtl()),
                                current.getDeviceId()));
        current.replaceWith(next, now);
        return new IssuedTokens(accessToken(user), raw, jwtProperties.accessTokenTtl());
    }

    /** Revokes the refresh token. Unknown or already revoked tokens are ignored. */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens
                .findByTokenHash(SecretTokens.hash(rawRefreshToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    @Transactional(readOnly = true)
    public User currentUser(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    }

    /** Changes the password and closes every other session. */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = currentUser(userId);
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        revokeAll(userId, clock.instant());
    }

    /**
     * Changes the PIN. The current PIN or the password proves the identity; the new PIN must be
     * unique in every restaurant of the user.
     */
    @Transactional
    public void changePin(Long userId, String currentSecret, String newPin) {
        User user = currentUser(userId);
        if (!matchesAnyCredential(user, currentSecret)) {
            throw invalidCredentials();
        }
        if (newPin == null || !PIN_FORMAT.matcher(newPin).matches()) {
            throw new BusinessRuleException(
                    AuthErrorCode.AUTH_PIN_INVALID_FORMAT, "The PIN must have 4 to 6 digits");
        }
        for (Long restaurantId : user.getRestaurantIds()) {
            boolean taken =
                    users.findPinUsersOfRestaurant(restaurantId).stream()
                            .filter(other -> !other.getId().equals(user.getId()))
                            .anyMatch(other -> passwordEncoder.matches(newPin, other.getPinHash()));
            if (taken) {
                throw new BusinessRuleException(
                        AuthErrorCode.AUTH_PIN_IN_USE,
                        "The PIN is already used in restaurant " + restaurantId);
            }
        }
        user.changePinHash(passwordEncoder.encode(newPin));
    }

    private boolean matchesAnyCredential(User user, String secret) {
        if (secret == null) {
            return false;
        }
        return (user.getPasswordHash() != null
                        && passwordEncoder.matches(secret, user.getPasswordHash()))
                || (user.getPinHash() != null
                        && passwordEncoder.matches(secret, user.getPinHash()));
    }

    private IssuedTokens issueSession(User user, Instant now) {
        String raw = SecretTokens.generate();
        refreshTokens.save(
                new RefreshToken(
                        user.getId(),
                        SecretTokens.hash(raw),
                        now.plus(jwtProperties.refreshTokenTtl()),
                        null));
        return new IssuedTokens(accessToken(user), raw, jwtProperties.accessTokenTtl());
    }

    private String accessToken(User user) {
        AuthenticatedUser principal =
                new AuthenticatedUser(
                        user.getId(),
                        user.getRestaurantIds(),
                        user.roleNames(),
                        permissionNames(user),
                        null);
        return jwtService.issue(principal, jwtProperties.accessTokenTtl());
    }

    private void revokeAll(Long userId, Instant now) {
        refreshTokens.findByUserIdAndRevokedAtIsNull(userId).forEach(t -> t.revoke(now));
    }

    private static Set<String> permissionNames(User user) {
        return user.permissions().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException(
                AuthErrorCode.AUTH_INVALID_CREDENTIALS, "Invalid credentials");
    }

    private static UnauthorizedException locked() {
        return new UnauthorizedException(
                AuthErrorCode.AUTH_ACCOUNT_LOCKED, "Too many failed attempts; try again later");
    }

    private static UnauthorizedException disabled() {
        return new UnauthorizedException(AuthErrorCode.AUTH_USER_DISABLED, "The user is disabled");
    }

    private static UnauthorizedException invalidDevice() {
        return new UnauthorizedException(
                AuthErrorCode.AUTH_INVALID_DEVICE, "The device is not registered or not active");
    }

    private static UnauthorizedException invalidRefreshToken() {
        return new UnauthorizedException(
                AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN, "The refresh token is not valid");
    }
}
