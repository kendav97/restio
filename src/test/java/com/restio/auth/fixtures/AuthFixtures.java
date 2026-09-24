package com.restio.auth.fixtures;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.restio.auth.application.SecretTokens;
import com.restio.auth.domain.Device;
import com.restio.auth.domain.DeviceType;
import com.restio.auth.domain.User;
import com.restio.auth.infrastructure.DeviceRepository;
import com.restio.auth.infrastructure.RoleRepository;
import com.restio.auth.infrastructure.UserRepository;

/** Creates users and devices for the auth tests and wipes them between tests. */
@Component
public class AuthFixtures {

    private final UserRepository users;
    private final RoleRepository roles;
    private final DeviceRepository devices;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public AuthFixtures(
            UserRepository users,
            RoleRepository roles,
            DeviceRepository devices,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbc) {
        this.users = users;
        this.roles = roles;
        this.devices = devices;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    public void clean() {
        jdbc.update("DELETE FROM sync_events");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM device_pairings");
        jdbc.update("DELETE FROM devices");
        jdbc.update("DELETE FROM users");
    }

    @Transactional
    public User user(String email, String password, String pin, String role, Long... restaurants) {
        User user = new User(email, email);
        if (password != null) {
            user.changePasswordHash(passwordEncoder.encode(password));
        }
        if (pin != null) {
            user.changePinHash(passwordEncoder.encode(pin));
        }
        user.assignRole(roles.findByName(role).orElseThrow());
        for (Long restaurantId : restaurants) {
            user.grantRestaurant(restaurantId);
        }
        return users.save(user);
    }

    @Transactional
    public Device device(Long restaurantId, String rawToken) {
        String code = Device.codeFor(devices.countEverLinked(restaurantId) + 1);
        return devices.save(
                new Device(restaurantId, code, "TPV", DeviceType.POS, SecretTokens.hash(rawToken)));
    }
}
