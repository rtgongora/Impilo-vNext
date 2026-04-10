package zw.gov.mohcc.impilo.experience;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

import java.util.Map;
import java.util.function.BooleanSupplier;

final class DockerOrExternalPostgresCondition implements ExecutionCondition {

    private static final String REASON =
            "Unsupported validation environment: start Docker/Testcontainers or set EXPERIENCE_BFF_TEST_JDBC_URL "
                    + "with optional EXPERIENCE_BFF_TEST_DB_USER / EXPERIENCE_BFF_TEST_DB_PASSWORD.";

    static ConditionEvaluationResult evaluate(Map<String, String> env, BooleanSupplier dockerAvailable) {
        if (ExperienceBffTestDatabaseSupport.hasExternalJdbc(env)) {
            return ConditionEvaluationResult.enabled("External Postgres configured for experience-bff integration tests.");
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
