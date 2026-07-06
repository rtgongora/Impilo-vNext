package zw.gov.mohcc.impilo.experience.trust;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Convention guard: the local docker-compose Envoy runtime config
 * (`infra/envoy/envoy-runtime.yaml`) must NOT route any live authorize/policy
 * trust path to the legacy default-ALLOW / fail-open `tshepo_service` cluster.
 *
 * <p>Background: the legacy `tshepo-service` monolith PolicyEngine is fail-open
 * and DEPRECATED-RETIRED. A previous local-compose split-brain forwarded these
 * REST routes to the dead monolith instead of the LIVE default-DENY
 * `tshepo-authz-service`. This guard prevents that split-brain from silently
 * returning.</p>
 */
class EnvoyRuntimeNoLegacyTshepoRouteGuardTest {

    /** Live authorize/policy trust paths that must reach the fail-closed authz engine. */
    private static final String[] GUARDED_PREFIXES = {
            "/api/v1/authorize",
            "/api/v1/policies",
            "/api/v1/step-up",
            "/api/v1/break-glass",
            "/api/v1/devices"
    };

    @Test
    void authorizeAndPolicyRoutesMustNotTargetLegacyFailOpenCluster() throws IOException {
        String yaml = Files.readString(locateEnvoyRuntimeYaml());

        for (String prefix : GUARDED_PREFIXES) {
            String cluster = clusterForPrefix(yaml, prefix);
            assertThat(cluster)
                    .as("Envoy route for %s must resolve to a cluster", prefix)
                    .isNotNull();
            assertThat(cluster)
                    .as("Envoy route for %s must NOT target the legacy fail-open tshepo_service cluster", prefix)
                    .isNotEqualTo("tshepo_service");
            assertThat(cluster)
                    .as("Envoy route for %s must target the live default-DENY tshepo_authz_service cluster", prefix)
                    .isEqualTo("tshepo_authz_service");
        }
    }

    private static String clusterForPrefix(String yaml, String prefix) {
        // Matches:  prefix: "/api/v1/authorize" }\n  route: { cluster: <name>
        Pattern p = Pattern.compile(
                "prefix:\\s*\"" + Pattern.quote(prefix) + "\"[^\\n]*\\n\\s*route:\\s*\\{\\s*cluster:\\s*([A-Za-z0-9_]+)");
        Matcher m = p.matcher(yaml);
        return m.find() ? m.group(1) : null;
    }

    /** Walk up from the module/working dir until infra/envoy/envoy-runtime.yaml is found. */
    private static Path locateEnvoyRuntimeYaml() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve("infra/envoy/envoy-runtime.yaml");
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not locate infra/envoy/envoy-runtime.yaml from " + Path.of("").toAbsolutePath());
    }
}
