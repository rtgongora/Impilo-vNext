package zw.gov.mohcc.impilo.ndila.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No mutating endpoint may be {@code permitAll} in this service.
 *
 * <p>The HPA import path was permitted on the grounds that it was "in-cluster only, not
 * gateway-routed externally". The namespace has <b>no NetworkPolicy at all</b>, so "internal" means
 * any pod can reach it — the premise never held. It then grew from one endpoint to five without the
 * matcher being revisited, which is how an unauthenticated surface widens without anyone deciding
 * to widen it.</p>
 *
 * <p>Asserted against the config source, because that is where the decision lives. A running-context
 * test would prove the current wiring works; this proves nobody quietly re-opens it.</p>
 */
class InternalEndpointAuthorizationTest {

    private static String securityConfig() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/zw/gov/mohcc/impilo/ndila/config/SecurityConfig.java"));
    }

    /** The specific regression: the HPA import/steward surface must require a role. */
    @Test
    void theHpaImportSurfaceIsNoLongerAnonymous() throws Exception {
        String config = securityConfig();
        assertThat(config)
                .as("the import surface writes locations and reads caller-supplied file paths")
                .doesNotContain("\"/internal/v1/locations/hpa-import/**\").permitAll()");
        assertThat(config).contains("\"/internal/v1/locations/hpa-import/**\")");
        assertThat(config).contains("hasAnyRole(NDILA_ADMIN_ROLES)");
    }

    /**
     * Nothing under {@code /internal/**} may be anonymous. If a future endpoint genuinely needs to
     * be, it should fail this test and force the argument to be had out loud — with a NetworkPolicy
     * to back it, since without one "internal" is not a boundary.
     */
    @Test
    void noInternalPathIsPermitAll() throws Exception {
        Pattern permitted = Pattern.compile(
                "requestMatchers\\(([^)]*?)\\)\\s*\\.permitAll\\(\\)", Pattern.DOTALL);
        Matcher matcher = permitted.matcher(securityConfig());
        while (matcher.find()) {
            String paths = matcher.group(1);
            assertThat(paths)
                    .as("permitAll on an internal path, with no NetworkPolicy to make it internal")
                    .doesNotContain("/internal/");
        }
    }

    /** Health and docs stay open — the point is mutating paths, not liveness probes. */
    @Test
    void operationalEndpointsRemainReachable() throws Exception {
        String config = securityConfig();
        List.of("/actuator/health", "/actuator/info").forEach(path ->
                assertThat(config).as("%s must stay permitted", path).contains(path));
    }
}
