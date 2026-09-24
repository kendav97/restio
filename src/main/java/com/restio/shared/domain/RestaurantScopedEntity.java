package com.restio.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import lombok.NoArgsConstructor;

/**
 * Base for entities that belong to a single restaurant. The {@code restaurantFilter} is enabled on
 * every transaction (see {@code com.restio.shared.tenant}), so a query can never return rows of a
 * restaurant the caller has no access to. Native queries bypass Hibernate filters and must filter
 * by {@code restaurant_id} themselves.
 */
@NoArgsConstructor
@MappedSuperclass
@FilterDef(
        name = RestaurantScopedEntity.FILTER_NAME,
        parameters = @ParamDef(name = RestaurantScopedEntity.FILTER_PARAM, type = Long.class))
@Filter(
        name = RestaurantScopedEntity.FILTER_NAME,
        condition = "restaurant_id IN (:" + RestaurantScopedEntity.FILTER_PARAM + ")")
public abstract class RestaurantScopedEntity extends AuditableEntity {

    public static final String FILTER_NAME = "restaurantFilter";
    public static final String FILTER_PARAM = "restaurantIds";

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private Long restaurantId;

    protected RestaurantScopedEntity(Long restaurantId) {
        this.restaurantId = restaurantId;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }
}
