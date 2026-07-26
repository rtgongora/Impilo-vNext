package zw.gov.mohcc.impilo.experience;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One Redis for the whole test JVM, one logical database per test class.
 *
 * <p><b>What this replaced.</b> Each of the six {@code @SpringBootTest} classes used to hold its
 * own instance of this support, start its own Testcontainers Redis in {@code @DynamicPropertySource},
 * and stop it again in {@code @AfterAll} — six container lifecycles per run where one would do.
 * Two things went wrong with that.</p>
 *
 * <p>First, every one of those six starts was a fresh chance to fail. A container that fails to
 * start throws out of {@code @DynamicPropertySource}, which fails the Spring context, which errors
 * <em>every test in that class</em> — including its {@code @Disabled} methods, which stop being
 * reported as skips. That is the shape behind the run that reported
 * {@code Errors: 20, Skipped: 2}: {@code ExperienceBffIntegrationTest} (10),
 * {@code StructuredHistoryApiIntegrationTest} (2) and {@code RbacIntegrationTest} (8) — the last
 * three classes in surefire's order, consecutive, consistent with a Docker daemon that degraded
 * partway through the run. With a single container the exposure is one start instead of six, and a
 * failure is total and obvious rather than partial and ordering-dependent.</p>
 *
 * <p>Second, {@code @AfterAll} stopped the container while the Spring context that pointed at it
 * stayed in the TestContext cache for the rest of the JVM. Testcontainers maps 6379 to a random
 * host port, so a container started later could be handed the port a stopped one had just
 * released — silently re-aiming a stale context at a live but wrong Redis. Nothing stops the
 * container now; Testcontainers' Ryuk reaper removes it when the JVM exits.</p>
 *
 * <p><b>Isolation.</b> Sharing one server would let idempotency keys, rate-limiter counters, OTPs
 * and cached workspace state leak between classes, which would trade a startup hazard for an
 * ordering hazard. Each class instead gets its own Redis logical database, so the keyspaces cannot
 * see each other and no flush between classes is needed.</p>
 *
 * <p>For environments without Docker, set {@code EXPERIENCE_BFF_TEST_REDIS_HOST} and optionally
 * {@code EXPERIENCE_BFF_TEST_REDIS_PORT} (default {@code 6379}); the per-class database split
 * applies there too.</p>
 */
final class ExperienceBffTestRedisSupport {

    static final String REDIS_HOST_ENV = "EXPERIENCE_BFF_TEST_REDIS_HOST";
    static final String REDIS_PORT_ENV = "EXPERIENCE_BFF_TEST_REDIS_PORT";

    /** Redis serves 16 logical databases by default; one class per database is the isolation unit. */
    static final int MAX_ISOLATED_CLASSES = 16;

    private static final Object CONTAINER_LOCK = new Object();
    private static final Map<String, Integer> DATABASE_BY_CLASS = new ConcurrentHashMap<>();

    private static GenericContainer<?> container;

    private ExperienceBffTestRedisSupport() {}

    /**
     * Points the given test class's Spring context at the shared Redis, on a database of its own.
     *
     * @param testClass the class declaring the {@code @DynamicPropertySource} — its identity is the
     *                  isolation key, so a context rebuilt after a cache eviction lands on the same
     *                  keyspace it had before
     */
    static void configure(Class<?> testClass, DynamicPropertyRegistry registry) {
        ResolvedRedisConfig config = ResolvedRedisConfig.fromEnv(System.getenv());
        int database = databaseFor(testClass);

        if (config.useEmbeddedContainer()) {
            GenericContainer<?> redis = sharedContainer();
            registry.add("spring.data.redis.host", redis::getHost);
            registry.add("spring.data.redis.port", () -> String.valueOf(redis.getMappedPort(6379)));
        } else {
            registry.add("spring.data.redis.host", config::host);
            registry.add("spring.data.redis.port", () -> String.valueOf(config.port()));
        }
        registry.add("spring.data.redis.database", () -> String.valueOf(database));
    }

    static boolean hasExternalRedis(Map<String, String> env) {
        return !env.getOrDefault(REDIS_HOST_ENV, "").trim().isEmpty();
    }

    /** Stable per-class database index, allocated on first sight of the class. */
    static int databaseFor(Class<?> testClass) {
        return DATABASE_BY_CLASS.computeIfAbsent(testClass.getName(), name -> {
            int next = DATABASE_BY_CLASS.size();
            if (next >= MAX_ISOLATED_CLASSES) {
                throw new IllegalStateException(
                        "More than " + MAX_ISOLATED_CLASSES + " test classes want an isolated Redis "
                                + "database, and Redis serves only that many. Give the shared container "
                                + "more with `--databases`, or flush between classes — but do not let "
                                + "two classes share a keyspace, because idempotency keys and "
                                + "rate-limiter counters leak across them. Offending class: " + name);
            }
            return next;
        });
    }

    private static GenericContainer<?> sharedContainer() {
        synchronized (CONTAINER_LOCK) {
            if (container == null) {
                GenericContainer<?> starting =
                        new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
                try {
                    starting.start();
                } catch (RuntimeException ex) {
                    throw new IllegalStateException(
                            """
                            Unable to start the shared Testcontainers Redis for experience-bff integration tests.
                            Start Docker, or set EXPERIENCE_BFF_TEST_REDIS_HOST (and optionally EXPERIENCE_BFF_TEST_REDIS_PORT).
                            """
                                    .strip(),
                            ex);
                }
                container = starting;
            }
            return container;
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
