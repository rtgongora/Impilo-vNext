package zw.gov.mohcc.impilo.experience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerOrExternalPostgresConditionTest {

    @Test
    @DisplayName("condition enables execution when external Redis is configured")
    void enablesForExternalRedis() {
        ConditionEvaluationResult result = DockerOrExternalPostgresCondition.evaluate(
                Map.of(ExperienceBffTestRedisSupport.REDIS_HOST_ENV, "127.0.0.1"), () -> false, () -> false);

        assertTrue(!result.isDisabled());
    }

    @Test
    @DisplayName("condition enables execution when Docker is available")
    void enablesForDocker() {
        ConditionEvaluationResult result = DockerOrExternalPostgresCondition.evaluate(Map.of(), () -> true, () -> false);

        assertTrue(!result.isDisabled());
    }

    @Test
    @DisplayName("condition disables execution with explicit guidance when neither Docker nor external Redis is available")
    void disablesWhenEnvironmentUnsupported() {
        ConditionEvaluationResult result = DockerOrExternalPostgresCondition.evaluate(Map.of(), () -> false, () -> false);

        assertTrue(result.isDisabled());
        assertTrue(result.getReason().orElse("").contains(ExperienceBffTestRedisSupport.REDIS_HOST_ENV));
    }
}
