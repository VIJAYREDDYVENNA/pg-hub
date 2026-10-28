package com.hostelmanagement.support;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.sql.Connection;
import java.sql.SQLException;

/** Starts every test run from an empty, freshly migrated schema, but only ever on a schema named "*_test". */
@TestConfiguration
public class IntegrationTestConfig {

    @Bean
    FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            String schema;
            try (Connection c = flyway.getConfiguration().getDataSource().getConnection()) {
                schema = c.getCatalog();
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot determine the test schema", e);
            }
            if (schema == null || !schema.endsWith("_test")) {
                throw new IllegalStateException("Refusing to clean a database that is not a *_test schema: " + schema);
            }
            flyway.clean();
            flyway.migrate();
        };
    }
}
