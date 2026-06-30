package zw.gov.mohcc.impilo.tshepo.authz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyRuleEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A3 bundle builder + an embedded Java->OPA round-trip (the A5 mechanism): the Java-generated bundle
 * is fed to the local `opa` against the A1/A4 rego and the verdict is asserted. The round-trip
 * self-skips when `opa` or the rego files are absent; the bundle-shape assertions always run.
 */
class PolicyRuleBundleBuilderTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final PolicyRuleBundleBuilder builder = new PolicyRuleBundleBuilder(M);

    private PolicyRuleEntity rule(String name, String effect, String actorType, String role, String action,
                                  String purpose, String resourceType, boolean facilityScope, int priority,
                                  String conditions) {
        PolicyRuleEntity r = new PolicyRuleEntity();
        r.setName(name);
        r.setTenantId(UUID.fromString("00000000-0000-4000-8000-000000000001"));
        r.setEffect(effect);
        r.setActorType(actorType);
        r.setRole(role);
        r.setAction(action);
        r.setPurpose(purpose);
        r.setResourceType(resourceType);
        r.setFacilityScope(facilityScope);
        r.setPriority(priority);
        r.setConditions(conditions);
        return r;
    }

    private List<PolicyRuleEntity> v018LikeRules() {
        return List.of(
                rule("allow-clinical-write", "ALLOW", "PROVIDER", "CLINICIAN", "POST", "TREATMENT",
                        "reports", true, 100, "{\"path_contains\":\"patient-safety/reports\",\"min_loa\":2}"),
                rule("deny-suspended", "DENY", "PROVIDER", "SUSPENDED", null, null, "reports", false, 10, null));
    }

    // ── bundle shape (always runs) ──────────────────────────────────────────

    @Test
    void bundle_has_policy_rules_with_parsed_conditions_and_string_tenant() throws Exception {
        String json = builder.buildBundleJson(v018LikeRules());
        JsonNode root = M.readTree(json);
        JsonNode rules = root.path("policy").path("rules");

        assertThat(rules.isArray()).isTrue();
        assertThat(rules).hasSize(2);
        // effect DESC: the DENY rule sorts before the ALLOW rule.
        assertThat(rules.get(0).path("effect").asText()).isEqualTo("DENY");
        JsonNode allowRule = rules.get(1);
        assertThat(allowRule.path("tenant_id").isTextual()).isTrue(); // UUID stringified
        assertThat(allowRule.path("facility_scope").asBoolean()).isTrue();
        // conditions parsed into an object, not a string.
        assertThat(allowRule.path("conditions").isObject()).isTrue();
        assertThat(allowRule.path("conditions").path("min_loa").asInt()).isEqualTo(2);
    }

    @Test
    void unparseable_conditions_emit_a_fail_closed_sentinel() throws Exception {
        var bad = List.of(rule("bad", "ALLOW", "PROVIDER", null, null, null, "reports", false, 100, "{not json"));
        JsonNode rules = M.readTree(builder.buildBundleJson(bad)).path("policy").path("rules");
        assertThat(rules.get(0).path("conditions").path("__unparseable__").asBoolean()).isTrue();
    }

    // ── Java -> OPA round-trip (self-skips without opa/rego) ─────────────────

    @Test
    void java_bundle_drives_opa_to_the_same_verdict() throws Exception {
        String opa = opaBinary();
        Path regoDir = repoRoot() == null ? null : repoRoot().resolve("infra/opa/impilo");
        assumeTrue(opa != null, "opa CLI not available");
        assumeTrue(regoDir != null && Files.exists(regoDir.resolve("policy_decision.rego")), "rego not found");

        Path bundle = Files.createTempFile("bundle", ".json");
        Files.writeString(bundle, builder.buildBundleJson(v018LikeRules()));

        // A fully-qualifying clinical request -> ALLOW.
        Map<String, Object> goodInput = Map.of(
                "tenant_id", "00000000-0000-4000-8000-000000000001",
                "actor_type", "PROVIDER", "roles", List.of("CLINICIAN"),
                "action", "POST:/internal/v1/patient-safety/reports", "purpose", "TREATMENT",
                "resource_type", "reports", "path", "/internal/v1/patient-safety/reports/1",
                "loa", 2, "assurance_level", "LOA2", "facility_id", "fac-1");
        assertThat(opaDecision(opa, regoDir, bundle, goodInput)).containsEntry("allow", true);

        // Same actor also carrying the SUSPENDED role -> the DENY rule wins.
        Map<String, Object> deniedInput = new java.util.HashMap<>(goodInput);
        deniedInput.put("roles", List.of("CLINICIAN", "SUSPENDED"));
        Map<String, Object> denied = opaDecision(opa, regoDir, bundle, deniedInput);
        assertThat(denied).containsEntry("allow", false);
        assertThat(denied).containsEntry("reason", "POLICY_DENY");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> opaDecision(String opa, Path regoDir, Path bundle, Map<String, Object> input)
            throws Exception {
        Path inputFile = Files.createTempFile("input", ".json");
        Files.writeString(inputFile, M.writeValueAsString(input));
        Process p = new ProcessBuilder(opa, "eval", "--format", "json",
                "-d", regoDir.resolve("conditions.rego").toString(),
                "-d", regoDir.resolve("policy_decision.rego").toString(),
                "-d", bundle.toString(), "-i", inputFile.toString(),
                "data.impilo.policy.decision")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        JsonNode value = M.readTree(out).at("/result/0/expressions/0/value");
        return M.convertValue(value, Map.class);
    }

    private static String opaBinary() {
        for (String c : new String[]{"opa", System.getProperty("user.home") + "/.local/bin/opa", "/usr/local/bin/opa"}) {
            try {
                new ProcessBuilder(c, "version").start().waitFor();
                return c;
            } catch (Exception ignored) {
                // try next candidate
            }
        }
        return null;
    }

    private static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("infra/opa/impilo/policy_decision.rego"))) {
            p = p.getParent();
        }
        return p;
    }
}
