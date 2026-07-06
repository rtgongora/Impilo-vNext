package zw.gov.mohcc.impilo.experience.trust;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Convention guard: the local docker-compose Envoy runtime config
 * (`infra/envoy/envoy-runtime.yaml`) must NOT route any live authorize/policy
 * trust path to the legacy default-ALLOW / fail-open `tshepo_service` cluster,
 * and each migrated non-PDP family must resolve to its canonical split-out
 * service cluster (never the legacy monolith).
 *
 * <p>Background: the legacy `tshepo-service` monolith PolicyEngine is fail-open
 * and DEPRECATED-RETIRED. A previous local-compose split-brain forwarded the
 * authorize/policy REST routes to the dead monolith instead of the LIVE
 * default-DENY `tshepo-authz-service`; W2-RED severed those. W2-RED-FOLLOWUP then
 * wired the split-out tshepo-identity/consent/audit/keys/offline services into
 * compose and re-pointed the 7 proven non-PDP families to them. This guard
 * prevents any of those from silently regressing to `tshepo_service`.</p>
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

    /**
     * Non-PDP families migrated to their canonical split-out clusters
     * (W2-RED-FOLLOWUP). Each MUST resolve to the mapped cluster and never to the
     * legacy fail-open `tshepo_service`. `/api/v1/keys`, `/api/v1/sign` and
     * `/api/v1/certificates` all resolve to the single keys service.
     */
    private static final Map<String, String> MIGRATED_SPLITOUT_PREFIXES = new LinkedHashMap<>() {{
        put("/api/v1/identity", "tshepo_identity_service");
        put("/api/v1/consent", "tshepo_consent_service");
        put("/api/v1/audit", "tshepo_audit_service");
        put("/api/v1/keys", "tshepo_keys_service");
        put("/api/v1/sign", "tshepo_keys_service");
        put("/api/v1/certificates", "tshepo_keys_service");
        put("/api/v1/offline", "tshepo_offline_service");
    }};

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

    @Test
    void migratedNonPdpRoutesMustTargetSplitOutClustersNotLegacy() throws IOException {
        String yaml = Files.readString(locateEnvoyRuntimeYaml());

        for (Map.Entry<String, String> entry : MIGRATED_SPLITOUT_PREFIXES.entrySet()) {
            String prefix = entry.getKey();
            String expectedCluster = entry.getValue();
            String cluster = clusterForPrefix(yaml, prefix);
            assertThat(cluster)
                    .as("Envoy route for %s must resolve to a cluster", prefix)
                    .isNotNull();
            assertThat(cluster)
                    .as("Envoy route for %s must NOT target the legacy fail-open tshepo_service cluster", prefix)
                    .isNotEqualTo("tshepo_service");
            assertThat(cluster)
                    .as("Envoy route for %s must target its canonical split-out cluster", prefix)
                    .isEqualTo(expectedCluster);
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
