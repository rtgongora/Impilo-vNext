package zw.gov.mohcc.impilo.experience;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Enables integration tests when Docker is available for Testcontainers Redis, or when an
 * external Redis host is configured via {@link ExperienceBffTestRedisSupport#REDIS_HOST_ENV}.
 */
final class DockerOrExternalPostgresCondition implements ExecutionCondition {

    private static final String REASON =
            "Unsupported validation environment: start Docker/Testcontainers for Redis, or set "
                    + ExperienceBffTestRedisSupport.REDIS_HOST_ENV
                    + " (optional "
                    + ExperienceBffTestRedisSupport.REDIS_PORT_ENV
                    + ").";

    static ConditionEvaluationResult evaluate(Map<String, String> env, BooleanSupplier dockerFactoryAvailable) {
        return evaluate(env, dockerFactoryAvailable, DockerCliProbe::dockerInfoSucceeds);
    }

    /**
     * Package-visible for tests: {@code cliDockerUp} can be stubbed so unit tests stay deterministic.
     */
    static ConditionEvaluationResult evaluate(
            Map<String, String> env,
            BooleanSupplier dockerFactoryAvailable,
            BooleanSupplier cliDockerUp) {
        if (ExperienceBffTestRedisSupport.hasExternalRedis(env)) {
            return ConditionEvaluationResult.enabled("External Redis configured for experience-bff integration tests.");
        }

        try {
            if (dockerFactoryAvailable.getAsBoolean()) {
                return ConditionEvaluationResult.enabled("Docker/Testcontainers is available for experience-bff integration tests.");
            }
        } catch (RuntimeException ignored) {
            // Fall through — try CLI probe (Docker Desktop 29.x + proxy issues).
        }

        if (cliDockerUp.getAsBoolean()) {
            return ConditionEvaluationResult.enabled(
                    "Docker CLI reports a healthy engine; integration tests will start Testcontainers Redis.");
        }

        return ConditionEvaluationResult.disabled(REASON);
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return evaluate(System.getenv(), () -> DockerClientFactory.instance().isDockerAvailable(), DockerCliProbe::dockerInfoSucceeds);
    }
}

