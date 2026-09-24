package com.restio.shared.tenant;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Restaurants the current thread is allowed to see. Populated per request from the JWT claims
 * (stage 0.3) and cleared when the request ends.
 *
 * <p>An empty context denies everything: {@link #allowedRestaurantIds()} then returns an identifier
 * that matches no row, so a missing context can never widen visibility.
 */
public final class RestaurantContext {

    /** Identifier used when nothing is allowed; no sequence ever generates it. */
    public static final Long NONE = -1L;

    private static final ThreadLocal<Set<Long>> ALLOWED = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SYSTEM = ThreadLocal.withInitial(() -> false);

    private RestaurantContext() {}

    public static void set(Set<Long> restaurantIds) {
        ALLOWED.set(Collections.unmodifiableSet(new LinkedHashSet<>(restaurantIds)));
    }

    public static Set<Long> allowedRestaurantIds() {
        Set<Long> allowed = ALLOWED.get();
        return allowed == null || allowed.isEmpty() ? Set.of(NONE) : allowed;
    }

    /** True while running inside a {@link SystemContext} method, where the filter is off. */
    public static boolean isSystem() {
        return Boolean.TRUE.equals(SYSTEM.get());
    }

    static void setSystem(boolean system) {
        SYSTEM.set(system);
    }

    public static void clear() {
        ALLOWED.remove();
        SYSTEM.remove();
    }
}
