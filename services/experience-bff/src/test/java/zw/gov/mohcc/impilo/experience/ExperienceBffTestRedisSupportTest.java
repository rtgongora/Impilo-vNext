package zw.gov.mohcc.impilo.experience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceBffTestRedisSupportTest {

    @Test
    @DisplayName("resolve defaults to embedded Testcontainers when no external Redis env is present")
    void defaultsToTestcontainers() {
        ExperienceBffTestRedisSupport.ResolvedRedisConfig config =
                ExperienceBffTestRedisSupport.ResolvedRedisConfig.fromEnv(Map.of());

        assertTrue(config.useEmbeddedContainer());
        assertNull(config.host());
        assertEquals(0, config.port());
    }

    @Test
    @DisplayName("resolve uses external Redis host and explicit port when provided")
    void usesExternalRedisAndExplicitPort() {
        ExperienceBffTestRedisSupport.ResolvedRedisConfig config =
                ExperienceBffTestRedisSupport.ResolvedRedisConfig.fromEnv(
                        Map.of(
                                ExperienceBffTestRedisSupport.REDIS_HOST_ENV, "redis.example",
                                ExperienceBffTestRedisSupport.REDIS_PORT_ENV, "6380"));

        assertFalse(config.useEmbeddedContainer());
        assertEquals("redis.example", config.host());
        assertEquals(6380, config.port());
    }

    @Test
    @DisplayName("resolve uses default port 6379 for external Redis when port is unset")
    void usesExternalRedisDefaultPort() {
        ExperienceBffTestRedisSupport.ResolvedRedisConfig config =
                ExperienceBffTestRedisSupport.ResolvedRedisConfig.fromEnv(
                        Map.of(ExperienceBffTestRedisSupport.REDIS_HOST_ENV, "127.0.0.1"));

        assertFalse(config.useEmbeddedContainer());
        assertEquals("127.0.0.1", config.host());
        assertEquals(6379, config.port());
    }
}
