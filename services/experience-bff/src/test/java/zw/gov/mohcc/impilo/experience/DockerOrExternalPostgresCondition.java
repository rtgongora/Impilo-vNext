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

    static ConditionEvaluationResult evaluate(Map<String, String> env, BooleanSupplier dockerAvailable) {
        if (ExperienceBffTestRedisSupport.hasExternalRedis(env)) {
            return ConditionEvaluationResult.enabled("External Redis configured for experience-bff integration tests.");
        }

        try {
            if (dockerAvailable.getAsBoolean()) {
                return ConditionEvaluationResult.enabled("Docker/Testcontainers is available for experience-bff integration tests.");
            }
        } catch (RuntimeException ignored) {
            // Fall through to a disabled result with explicit operator guidance.
        }

        return ConditionEvaluationResult.disabled(REASON);
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return evaluate(System.getenv(), () -> DockerClientFactory.instance().isDockerAvailable());
    }
}
