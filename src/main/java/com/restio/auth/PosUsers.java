package com.restio.auth;

import java.util.List;
import java.util.Set;

/**
 * Users who can log in with a PIN on the devices of a restaurant. The devices keep a copy (with the
 * PIN hash, never the PIN) to accept PIN logins without internet.
 */
public interface PosUsers {

    List<PosUser> ofRestaurant(Long restaurantId);

    record PosUser(
            Long id,
            String displayName,
            String pinHash,
            Set<String> roles,
            Set<String> permissions) {}
}
