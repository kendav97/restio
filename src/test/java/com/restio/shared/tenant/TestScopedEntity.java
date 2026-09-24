package com.restio.shared.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.restio.shared.domain.RestaurantScopedEntity;

/** Minimal restaurant-scoped entity used to exercise the isolation filter and the audit stamps. */
@Entity
@Table(name = "test_scoped_entities")
public class TestScopedEntity extends RestaurantScopedEntity {

    @Column(name = "name", nullable = false)
    private String name;

    protected TestScopedEntity() {}

    public TestScopedEntity(Long restaurantId, String name) {
        super(restaurantId);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
