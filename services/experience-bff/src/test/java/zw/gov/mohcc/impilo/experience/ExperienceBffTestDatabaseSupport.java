package zw.gov.mohcc.impilo.experience;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

/**
 * Shared integration-test database preflight for experience-bff.
 *
 * <p>By default we start a real Testcontainers Postgres. When Docker/Testcontainers
 * is unavailable, callers can point the tests at a reachable Postgres by exporting:
 * {@code EXPERIENCE_BFF_TEST_JDBC_URL}, and optionally
 * {@code EXPERIENCE_BFF_TEST_DB_USER}/{@code EXPERIENCE_BFF_TEST_DB_PASSWORD}.</p>
 */
final class ExperienceBffTestDatabaseSupport {

    static final String JDBC_ENV = "EXPERIENCE_BFF_TEST_JDBC_URL";
    static final String USER_ENV = "EXPERIENCE_BFF_TEST_DB_USER";
    static final String PASSWORD_ENV = "EXPERIENCE_BFF_TEST_DB_PASSWORD";

    private final ResolvedDatabaseConfig config;
    private PostgreSQLContainer<?> postgres;

    private ExperienceBffTestDatabaseSupport(ResolvedDatabaseConfig config, PostgreSQLContainer<?> postgres) {
        this.config = config;
        this.postgres = postgres;
    }

    static ExperienceBffTestDatabaseSupport fromEnvironment(String databaseName) {
        ResolvedDatabaseConfig config = resolve(System.getenv(), databaseName);
        return new ExperienceBffTestDatabaseSupport(config, null);
    }

    static ResolvedDatabaseConfig resolve(Map<String, String> env, String databaseName) {
        String externalJdbc = env.getOrDefault(JDBC_ENV, "").trim();
        if (!externalJdbc.isEmpty()) {
            return new ResolvedDatabaseConfig(
                    false,
                    databaseName,
                    externalJdbc,
                    env.getOrDefault(USER_ENV, "impilo"),
                    env.getOrDefault(PASSWORD_ENV, "impilo")
            );
        }

        return new ResolvedDatabaseConfig(
                true,
                databaseName,
                null,
                "test",
                "test"
        );
    }

    static boolean hasExternalJdbc(Map<String, String> env) {
        return !env.getOrDefault(JDBC_ENV, "").trim().isEmpty();
    }

    void configure(DynamicPropertyRegistry registry) {
        if (config.useTestcontainers()) {
            PostgreSQLContainer<?> started = ensureStarted();
            registry.add("spring.datasource.url", started::getJdbcUrl);
            registry.add("spring.datasource.username", started::getUsername);
            registry.add("spring.datasource.password", started::getPassword);
        } else {
            registry.add("spring.datasource.url", config::jdbcUrl);
            registry.add("spring.datasource.username", config::username);
            registry.add("spring.datasource.password", config::password);
        }
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    private synchronized PostgreSQLContainer<?> ensureStarted() {
        if (postgres != null) {
            return postgres;
        }

        PostgreSQLContainer<?> started = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName(config.databaseName())
                .withUsername(config.username())
                .withPassword(config.password());

        try {
            started.start();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("""
                    Unable to start Testcontainers Postgres for experience-bff integration tests.
                    To run without Docker, export EXPERIENCE_BFF_TEST_JDBC_URL and optionally
                    EXPERIENCE_BFF_TEST_DB_USER / EXPERIENCE_BFF_TEST_DB_PASSWORD, then rerun the Maven test.
                    """.strip(), ex);
        }

        postgres = started;
        return postgres;
    }

    void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    record ResolvedDatabaseConfig(
            boolean useTestcontainers,
            String databaseName,
            String jdbcUrl,
            String username,
            String password
    ) {}
}
