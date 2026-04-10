package zw.gov.mohcc.impilo.experience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerOrExternalPostgresConditionTest {

    @Test
    @DisplayName("condition enables execution when external JDBC is configured")
    void enablesForExternalJdbc() {
        ConditionEvaluationResult result = DockerOrExternalPostgresCondition.evaluate(
                Map.of(ExperienceBffTestDatabaseSupport.JDBC_ENV, "jdbc:postgresql://localhost:5433/experience_bff"),
                () -> false
        );

        assertTrue(!result.isDisabled());
    }

    @Test
    @DisplayName("condition enables execution when Docker is available")
    void enablesForDocker() {
        ConditionEvaluationResult result = DockerOrExternalPostgresCondition.evaluate(Map.of(), () -> true);

        assertTrue(!result.isDisabled());
    }

    @Test
    @DisplayName("condition disables execution with explicit guidance when neither Docker nor external JDBC is available")
    void disablesWhenEnvironmentUnsupported() {
        ConditionEvaluationResult result = DockerOrExternalPostgresCondition.evaluate(Map.of(), () -> false);

        assertTrue(result.isDisabled());
        assertTrue(result.getReason().orElse("").contains("EXPERIENCE_BFF_TEST_JDBC_URL"));
    }
}
