package com.fila.ciclica.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(ProofReporter.class)
class ContextLoadIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("context loads and Flyway applies the schema")
    void contextLoads() {
        assertThat(applicationContext).isNotNull();

        Integer flywayHistoryTables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = database() and table_name = 'flyway_schema_history'",
                Integer.class);

        assertThat(flywayHistoryTables).isEqualTo(1);
    }
}
