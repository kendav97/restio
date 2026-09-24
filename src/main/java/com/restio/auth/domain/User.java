package com.restio.auth.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import com.restio.auth.Permission;
import com.restio.shared.domain.AuditableEntity;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A person who can access Restio: with email and password in the backoffice, with a PIN on a
 * registered device, or both. Not scoped to one restaurant: {@link #getRestaurantIds()} lists the
 * restaurants the user may work in.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "pin_hash")
    private String pinHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_restaurants", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "restaurant_id", nullable = false)
    private Set<Long> restaurantIds = new HashSet<>();

    public User(String email, String displayName) {
        this.email = email;
        this.displayName = displayName;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    public Set<Long> getRestaurantIds() {
        return Set.copyOf(restaurantIds);
    }

    public Set<String> roleNames() {
        return roles.stream().map(Role::getName).collect(Collectors.toUnmodifiableSet());
    }

    /** Union of the permissions of every role. */
    public Set<Permission> permissions() {
        Set<Permission> all = EnumSet.noneOf(Permission.class);
        roles.forEach(role -> all.addAll(role.getPermissions()));
        return all;
    }

    public void assignRole(Role role) {
        roles.add(role);
    }

    public void grantRestaurant(Long restaurantId) {
        restaurantIds.add(restaurantId);
    }

    public boolean worksIn(Long restaurantId) {
        return restaurantIds.contains(restaurantId);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** Counts a failed login; reaching the limit locks the account for the policy's duration. */
    public void registerFailedAttempt(Instant now, LoginLock policy) {
        failedAttempts++;
        if (failedAttempts >= policy.maxFailedAttempts()) {
            lockedUntil = now.plus(policy.lockDuration());
            failedAttempts = 0;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changePinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public void disable() {
        enabled = false;
    }
}
