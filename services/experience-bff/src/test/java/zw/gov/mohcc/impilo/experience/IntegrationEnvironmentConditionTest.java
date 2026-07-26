package zw.gov.mohcc.impilo.experience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationEnvironmentConditionTest {

    @Test
    @DisplayName("condition enables execution when external Redis is configured")
    void enablesForExternalRedis() {
        ConditionEvaluationResult result = IntegrationEnvironmentCondition.evaluate(
                Map.of(ExperienceBffTestRedisSupport.REDIS_HOST_ENV, "127.0.0.1"), () -> false, () -> false);

        assertFalse(result.isDisabled());
    }

    @Test
    @DisplayName("condition enables execution when Docker is available")
    void enablesForDocker() {
        ConditionEvaluationResult result =
                IntegrationEnvironmentCondition.evaluate(Map.of(), () -> true, () -> false);

        assertFalse(result.isDisabled());
    }

    @Test
    @DisplayName("condition enables execution when only the Docker CLI probe succeeds")
    void enablesForDockerCliProbe() {
        ConditionEvaluationResult result =
                IntegrationEnvironmentCondition.evaluate(Map.of(), () -> false, () -> true);

        assertFalse(result.isDisabled());
    }

    @Test
    @DisplayName("a missing integration environment fails loudly instead of silently skipping 44 tests")
    void failsWhenEnvironmentUnsupported() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> IntegrationEnvironmentCondition.evaluate(Map.of(), () -> false, () -> false));

        assertTrue(thrown.getMessage().contains(ExperienceBffTestRedisSupport.REDIS_HOST_ENV),
                "the message must say how to supply an environment");
        assertTrue(thrown.getMessage().contains(IntegrationEnvironmentCondition.SKIP_INTEGRATION_ENV),
                "the message must say how to opt out deliberately");
    }

    @Test
    @DisplayName("skipping is available, but only when asked for explicitly")
    void skipsOnlyWhenRequested() {
        ConditionEvaluationResult result = IntegrationEnvironmentCondition.evaluate(
                Map.of(IntegrationEnvironmentCondition.SKIP_INTEGRATION_ENV, "true"), () -> false, () -> false);

        assertTrue(result.isDisabled());
        assertTrue(result.getReason().orElse("").contains(IntegrationEnvironmentCondition.SKIP_INTEGRATION_ENV));
    }

    @Test
    @DisplayName("the opt-out is honoured even where Docker is available")
    void skipRequestBeatsAWorkingEnvironment() {
        ConditionEvaluationResult result = IntegrationEnvironmentCondition.evaluate(
                Map.of(IntegrationEnvironmentCondition.SKIP_INTEGRATION_ENV, "true"), () -> true, () -> true);

        assertTrue(result.isDisabled(),
                "an opt-out that only works once the environment is already broken is not an opt-out");
    }

    @Test
    @DisplayName("an unset or falsey opt-out is not an opt-out")
    void falseyOptOutStillFails() {
        assertFalse(IntegrationEnvironmentCondition.skipRequested(Map.of()));
        assertFalse(IntegrationEnvironmentCondition.skipRequested(
                Map.of(IntegrationEnvironmentCondition.SKIP_INTEGRATION_ENV, "false")));
        assertFalse(IntegrationEnvironmentCondition.skipRequested(
                Map.of(IntegrationEnvironmentCondition.SKIP_INTEGRATION_ENV, "")));
        assertTrue(IntegrationEnvironmentCondition.skipRequested(
                Map.of(IntegrationEnvironmentCondition.SKIP_INTEGRATION_ENV, "1")));
    }
}
