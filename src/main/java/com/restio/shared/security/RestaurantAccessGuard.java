package com.restio.shared.security;

import org.springframework.stereotype.Component;

import com.restio.shared.tenant.RestaurantContext;

/**
 * Checks that the caller belongs to the restaurant in the path. Used from method security:
 * {@code @PreAuthorize("@restaurantAccess.canAccess(#restaurantId) and hasAuthority('X')")}.
 */
@Component("restaurantAccess")
public class RestaurantAccessGuard {

    public boolean canAccess(Long restaurantId) {
        return restaurantId != null
                && !RestaurantContext.NONE.equals(restaurantId)
                && RestaurantContext.allowedRestaurantIds().contains(restaurantId);
    }
}
