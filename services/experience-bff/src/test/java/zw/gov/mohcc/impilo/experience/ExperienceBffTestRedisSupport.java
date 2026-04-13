package zw.gov.mohcc.impilo.experience;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Shared Redis preflight for experience-bff integration tests (stateless BFF — no PostgreSQL).
 *
 * <p>Starts Testcontainers Redis when Docker is available. For environments without Docker,
 * set {@code EXPERIENCE_BFF_TEST_REDIS_HOST} and optionally {@code EXPERIENCE_BFF_TEST_REDIS_PORT}
 * (default {@code 6379}).</p>
 */
final class ExperienceBffTestRedisSupport {

    static final String REDIS_HOST_ENV = "EXPERIENCE_BFF_TEST_REDIS_HOST";
    static final String REDIS_PORT_ENV = "EXPERIENCE_BFF_TEST_REDIS_PORT";

    private final ResolvedRedisConfig config;
    private GenericContainer<?> redis;

    private ExperienceBffTestRedisSupport(ResolvedRedisConfig config) {
        this.config = config;
    }

    static ExperienceBffTestRedisSupport fromEnvironment() {
        return new ExperienceBffTestRedisSupport(ResolvedRedisConfig.fromEnv(System.getenv()));
    }

    static boolean hasExternalRedis(Map<String, String> env) {
        return !env.getOrDefault(REDIS_HOST_ENV, "").trim().isEmpty();
    }

    void configure(DynamicPropertyRegistry registry) {
        if (config.useEmbeddedContainer()) {
            ensureStarted();
            registry.add("spring.data.redis.host", redis::getHost);
            registry.add("spring.data.redis.port", () -> String.valueOf(redis.getMappedPort(6379)));
        } else {
            registry.add("spring.data.redis.host", config::host);
            registry.add("spring.data.redis.port", () -> String.valueOf(config.port()));
        }
    }

    void stop() {
        if (redis != null) {
            redis.stop();
        }
    }

    private synchronized void ensureStarted() {
        if (redis != null) {
            return;
        }
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        try {
            redis.start();
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    """
                    Unable to start Testcontainers Redis for experience-bff integration tests.
                    Start Docker, or set EXPERIENCE_BFF_TEST_REDIS_HOST (and optionally EXPERIENCE_BFF_TEST_REDIS_PORT).
                    """
                            .strip(),
                    ex);
        }
    }

    record ResolvedRedisConfig(boolean useEmbeddedContainer, String host, int port) {
        static ResolvedRedisConfig fromEnv(Map<String, String> env) {
            String h = env.getOrDefault(REDIS_HOST_ENV, "").trim();
            if (!h.isEmpty()) {
                int p = Integer.parseInt(env.getOrDefault(REDIS_PORT_ENV, "6379").trim());
                return new ResolvedRedisConfig(false, h, p);
            }
            return new ResolvedRedisConfig(true, null, 0);
        }
    }
}
