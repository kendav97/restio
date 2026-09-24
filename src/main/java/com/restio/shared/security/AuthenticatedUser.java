package com.restio.shared.security;

import java.security.Principal;
import java.util.Set;

/**
 * The caller of the current request, rebuilt from the access token claims. {@code deviceId} is set
 * for tokens obtained from a registered device: a PIN session (a user on a device) or a device
 * session (the device itself, with no user, used for synchronisation).
 */
public record AuthenticatedUser(
        Long userId,
        Set<Long> restaurantIds,
        Set<String> roles,
        Set<String> permissions,
        Long deviceId)
        implements Principal {

    /** Role of device sessions; it grants no user permission. */
    public static final String DEVICE_ROLE = "DEVICE";

    static final String DEVICE_NAME_PREFIX = "device:";

    public AuthenticatedUser {
        restaurantIds = Set.copyOf(restaurantIds);
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
        if (userId == null && deviceId == null) {
            throw new IllegalArgumentException("A caller is a user, a device or both");
        }
    }

    /** The session of a device acting on its own, limited to its restaurant. */
    public static AuthenticatedUser device(Long deviceId, Long restaurantId) {
        return new AuthenticatedUser(
                null, Set.of(restaurantId), Set.of(DEVICE_ROLE), Set.of(), deviceId);
    }

    public boolean isDevice() {
        return userId == null;
    }

    /** The user identifier, or {@code device:<id>} for a device session; the audit records it. */
    @Override
    public String getName() {
        return isDevice() ? DEVICE_NAME_PREFIX + deviceId : String.valueOf(userId);
    }
}
