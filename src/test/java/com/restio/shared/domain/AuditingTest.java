package com.restio.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.restio.AbstractIntegrationTest;
import com.restio.shared.tenant.RestaurantContext;
import com.restio.shared.tenant.TestScopedEntity;
import com.restio.shared.tenant.TestScopedEntityRepository;

@Transactional
class AuditingTest extends AbstractIntegrationTest {

    private static final long RESTAURANT_A = 1L;

    @Autowired private TestScopedEntityRepository repository;

    @AfterEach
    void clearContext() {
        RestaurantContext.clear();
    }

    @Test
    @DisplayName("al guardar se rellenan las marcas de auditoría y la versión")
    void save_newEntity_fillsAuditStampsAndVersion() {
        RestaurantContext.set(Set.of(RESTAURANT_A));

        TestScopedEntity saved =
                repository.saveAndFlush(new TestScopedEntity(RESTAURANT_A, "mesa 1"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo("system");
        assertThat(saved.getVersion()).isZero();
    }
}
