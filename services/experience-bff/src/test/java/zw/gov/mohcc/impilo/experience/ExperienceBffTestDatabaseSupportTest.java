package zw.gov.mohcc.impilo.experience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceBffTestDatabaseSupportTest {

    @Test
    @DisplayName("resolve defaults to Testcontainers when no external JDBC env is present")
    void defaultsToTestcontainers() {
        ExperienceBffTestDatabaseSupport.ResolvedDatabaseConfig config =
                ExperienceBffTestDatabaseSupport.resolve(Map.of(), "experience_bff_structured_history");

        assertTrue(config.useTestcontainers());
        assertEquals("experience_bff_structured_history", config.databaseName());
        assertNull(config.jdbcUrl());
        assertEquals("test", config.username());
        assertEquals("test", config.password());
    }

    @Test
    @DisplayName("resolve uses external JDBC and explicit credentials when provided")
    void usesExternalJdbcAndExplicitCredentials() {
        ExperienceBffTestDatabaseSupport.ResolvedDatabaseConfig config =
                ExperienceBffTestDatabaseSupport.resolve(Map.of(
                        ExperienceBffTestDatabaseSupport.JDBC_ENV, "jdbc:postgresql://localhost:5433/experience_bff",
                        ExperienceBffTestDatabaseSupport.USER_ENV, "postgres",
                        ExperienceBffTestDatabaseSupport.PASSWORD_ENV, "postgres"
                ), "ignored_for_external");

        assertFalse(config.useTestcontainers());
        assertEquals("jdbc:postgresql://localhost:5433/experience_bff", config.jdbcUrl());
        assertEquals("postgres", config.username());
        assertEquals("postgres", config.password());
    }

    @Test
    @DisplayName("resolve falls back to impilo credentials for external JDBC when user/password are unset")
    void usesExternalJdbcDefaultCredentials() {
        ExperienceBffTestDatabaseSupport.ResolvedDatabaseConfig config =
                ExperienceBffTestDatabaseSupport.resolve(Map.of(
                        ExperienceBffTestDatabaseSupport.JDBC_ENV, "jdbc:postgresql://localhost:5433/experience_bff"
                ), "ignored_for_external");

        assertFalse(config.useTestcontainers());
        assertEquals("jdbc:postgresql://localhost:5433/experience_bff", config.jdbcUrl());
        assertEquals("impilo", config.username());
        assertEquals("impilo", config.password());
    }
}
