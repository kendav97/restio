package com.restio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ApplicationContextTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("el contexto arranca y Flyway aplica la migración base")
    void contextLoads_withFlywayBaseline_createsEventPublicationTable() {
        Integer tables =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM information_schema.tables"
                                + " WHERE table_name = 'event_publication'",
                        Integer.class);

        assertThat(tables).isEqualTo(1);
    }
}
