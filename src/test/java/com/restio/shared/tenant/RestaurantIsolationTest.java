package com.restio.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.restio.AbstractIntegrationTest;

@Transactional
class RestaurantIsolationTest extends AbstractIntegrationTest {

    private static final long RESTAURANT_A = 1L;
    private static final long RESTAURANT_B = 2L;

    @Autowired private TestScopedEntityRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SystemContextFixture systemFixture;

    @BeforeEach
    void seedBothRestaurants() {
        jdbcTemplate.update("DELETE FROM test_scoped_entities");
        insert(10L, RESTAURANT_A, "mesa A");
        insert(20L, RESTAURANT_B, "mesa B");
    }

    @AfterEach
    void clearContext() {
        RestaurantContext.clear();
    }

    @Test
    @DisplayName("una consulta solo devuelve filas de los locales permitidos")
    void findAll_withOneAllowedRestaurant_returnsOnlyItsRows() {
        RestaurantContext.set(Set.of(RESTAURANT_A));

        List<TestScopedEntity> found = repository.findAll();

        assertThat(found).extracting(TestScopedEntity::getName).containsExactly("mesa A");
    }

    @Test
    @DisplayName("sin contexto de local no se ve ninguna fila")
    void findAll_withoutContext_returnsNothing() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("una fila de otro local no se puede obtener por id")
    void findById_ofAnotherRestaurant_returnsEmpty() {
        RestaurantContext.set(Set.of(RESTAURANT_A));

        assertThat(repository.findById(20L)).isEmpty();
    }

    @Test
    @DisplayName("varios locales permitidos devuelven las filas de todos")
    void findAll_withSeveralAllowedRestaurants_returnsAllTheirRows() {
        RestaurantContext.set(Set.of(RESTAURANT_A, RESTAURANT_B));

        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("un método de sistema ve todos los locales")
    void findAll_insideSystemContext_ignoresTheFilter() {
        RestaurantContext.set(Set.of(RESTAURANT_A));

        assertThat(systemFixture.countAll()).isEqualTo(2);
    }

    private void insert(long id, long restaurantId, String name) {
        jdbcTemplate.update(
                "INSERT INTO test_scoped_entities"
                        + " (id, restaurant_id, name, created_at, updated_at, version)"
                        + " VALUES (?, ?, ?, now(), now(), 0)",
                id,
                restaurantId,
                name);
    }
}
