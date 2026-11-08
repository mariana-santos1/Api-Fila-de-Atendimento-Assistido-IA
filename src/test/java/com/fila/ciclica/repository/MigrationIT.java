package com.fila.ciclica.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fila.ciclica.support.AbstractIntegrationTest;
import com.fila.ciclica.support.ProofReporter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(ProofReporter.class)
class MigrationIT extends AbstractIntegrationTest {

    private static final List<String> EXPECTED_TABLES =
            List.of("users", "queues", "cycles", "tickets", "cycle_calls");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V1 migration creates all tables, columns and unique constraints")
    void migrationCreatesSchema() {
        Integer appliedVersion = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history "
                        + "where version = '1' and success = 1",
                Integer.class);
        assertThat(appliedVersion).isEqualTo(1);

        for (String table : EXPECTED_TABLES) {
            Integer tableCount = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.tables "
                            + "where table_schema = database() and table_name = ?",
                    Integer.class,
                    table);
            assertThat(tableCount)
                    .as("table %s must exist", table)
                    .isEqualTo(1);
        }

        assertUniqueConstraint("uk_users_email");
        assertUniqueConstraint("uk_queues_name");
        assertUniqueConstraint("uk_queues_prefix");
        assertUniqueConstraint("uk_cycles_queue_number");
        assertUniqueConstraint("uk_cycles_active_queue");
        assertUniqueConstraint("uk_tickets_public_id");
        assertUniqueConstraint("uk_tickets_queue_code");
        assertUniqueConstraint("uk_tickets_queue_external_ref");
        assertUniqueConstraint("uk_calls_cycle_sequence");

        assertForeignKey("fk_cycles_queue", "cycles", "queues");
        assertForeignKey("fk_tickets_queue", "tickets", "queues");
        assertForeignKey("fk_tickets_cycle", "tickets", "cycles");
        assertForeignKey("fk_calls_cycle", "cycle_calls", "cycles");
        assertForeignKey("fk_calls_ticket", "cycle_calls", "tickets");
        assertForeignKey("fk_calls_attendant", "cycle_calls", "users");

        assertNullable("cycles", "active_queue_id");
        assertNullable("tickets", "external_reference");
        assertDateTimePrecision("users", "created_at");
        assertDateTimePrecision("tickets", "called_at");
        assertDateTimePrecision("cycle_calls", "called_at");
    }

    private void assertUniqueConstraint(String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.table_constraints "
                        + "where table_schema = database() and constraint_name = ? "
                        + "and constraint_type = 'UNIQUE'",
                Integer.class,
                constraintName);
        assertThat(count)
                .as("unique constraint %s must exist", constraintName)
                .isEqualTo(1);
    }

    private void assertForeignKey(String constraintName, String table, String referencedTable) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.table_constraints "
                        + "where table_schema = database() and constraint_name = ? "
                        + "and constraint_type = 'FOREIGN KEY' and table_name = ?",
                Integer.class,
                constraintName,
                table);
        assertThat(count)
                .as("foreign key %s on %s must exist", constraintName, table)
                .isEqualTo(1);

        Integer referenced = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.key_column_usage "
                        + "where table_schema = database() and constraint_name = ? "
                        + "and referenced_table_name = ?",
                Integer.class,
                constraintName,
                referencedTable);
        assertThat(referenced)
                .as("foreign key %s must reference %s", constraintName, referencedTable)
                .isGreaterThanOrEqualTo(1);
    }

    private void assertNullable(String table, String column) {
        String nullable = jdbcTemplate.queryForObject(
                "select is_nullable from information_schema.columns "
                        + "where table_schema = database() and table_name = ? and column_name = ?",
                String.class,
                table,
                column);
        assertThat(nullable).isEqualTo("YES");
    }

    private void assertDateTimePrecision(String table, String column) {
        Integer precision = jdbcTemplate.queryForObject(
                "select datetime_precision from information_schema.columns "
                        + "where table_schema = database() and table_name = ? and column_name = ?",
                Integer.class,
                table,
                column);
        assertThat(precision).isEqualTo(6);
    }
}
