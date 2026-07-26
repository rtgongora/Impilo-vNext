package zw.gov.mohcc.impilo.experience;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Requires a usable integration environment — Docker for Testcontainers Redis, or an external
 * Redis via {@link ExperienceBffTestRedisSupport#REDIS_HOST_ENV} — and says so out loud when
 * there isn't one.
 *
 * <p><b>Why it fails rather than skips.</b> This condition gates the 44 tests in the six
 * {@code @SpringBootTest} classes, which are the only integration coverage experience-bff has.
 * It used to return {@code disabled} when neither Docker nor an external Redis was found, so a
 * wedged daemon — or a 25-second {@code docker info} that timed out under load — silently deleted
 * every one of them and the build still exited 0. A gate that quietly stops testing anything and
 * reports success is the same class of failure as a BFF answering an unreachable service with an
 * empty 200: the absence of a signal presented as a clean result.</p>
 *
 * <p>Skipping is still available, but it has to be asked for: set
 * {@value #SKIP_INTEGRATION_ENV} to {@code true}/{@code 1}/{@code yes}. Then the skip is a
 * decision someone made and can be seen in the run configuration, rather than something the
 * environment did on its own.</p>
 */
final class IntegrationEnvironmentCondition implements ExecutionCondition {

    /** Explicit opt-out for environments that genuinely cannot run containers. */
    static final String SKIP_INTEGRATION_ENV = "EXPERIENCE_BFF_SKIP_INTEGRATION";

    static final String MISSING_ENVIRONMENT_MESSAGE =
            "experience-bff integration tests need Docker (for Testcontainers Redis) or an external Redis, "
                    + "and found neither. This is the service's only integration coverage, so it fails here "
                    + "rather than skipping silently and reporting success.\n"
                    + "  • start Docker, or\n"
                    + "  • set " + ExperienceBffTestRedisSupport.REDIS_HOST_ENV
                    + " (and optionally " + ExperienceBffTestRedisSupport.REDIS_PORT_ENV + "), or\n"
                    + "  • set " + SKIP_INTEGRATION_ENV + "=true to skip them deliberately.";

    static ConditionEvaluationResult evaluate(Map<String, String> env, BooleanSupplier dockerFactoryAvailable) {
        return evaluate(env, dockerFactoryAvailable, DockerCliProbe::dockerInfoSucceeds);
    }

    /**
     * Package-visible for tests: {@code cliDockerUp} can be stubbed so unit tests stay deterministic.
     *
     * @throws IllegalStateException when no integration environment is available and the skip has
     *                               not been requested explicitly
     */
    static ConditionEvaluationResult evaluate(
            Map<String, String> env,
            BooleanSupplier dockerFactoryAvailable,
            BooleanSupplier cliDockerUp) {
        // Checked first, so the opt-out is honoured wherever it is set — including on a machine
        // that does have Docker. An opt-out that only works when the environment is already
        // broken is not an opt-out.
        if (skipRequested(env)) {
            return ConditionEvaluationResult.disabled(
                    "experience-bff integration tests skipped deliberately via " + SKIP_INTEGRATION_ENV + ".");
        }

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
                    "Docker CLI reports a healthy engine; integration tests will use the shared Testcontainers Redis.");
        }

        throw new IllegalStateException(MISSING_ENVIRONMENT_MESSAGE);
    }

    static boolean skipRequested(Map<String, String> env) {
        String value = env.getOrDefault(SKIP_INTEGRATION_ENV, "").trim();
        return value.equalsIgnoreCase("true") || value.equals("1") || value.equalsIgnoreCase("yes");
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return evaluate(System.getenv(), () -> DockerClientFactory.instance().isDockerAvailable(), DockerCliProbe::dockerInfoSucceeds);
    }
}
