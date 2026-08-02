package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the OPA shadow input contract against the policy that consumes it.
 *
 * <p>The bug this exists to prevent already happened: the inline mapper sent {@code identity_loa}
 * while {@code infra/opa/authz/authz.rego} reads {@code input.loa} / {@code input.assurance_loa}.
 * Because the policy's accessors default to 0 on a missing key, nothing failed — the comparison
 * simply measured a typo, and would have reported MIN_LOA divergence on essentially all traffic.
 * A test that asserted only the Java side would not have caught it, so these tests read the
 * <em>actual policy file</em> and assert the two agree.</p>
 */
class OpaShadowInputMapperTest {

    private static final Path REGO = Path.of("../../infra/opa/authz/authz.rego");

    private static AuthzInternalRequest request(String actorId, String subjectId, String providerId,
                                                String assuranceLevel, DutyContext duty) {
        return new AuthzInternalRequest(
                UUID.randomUUID(), actorId, "PROVIDER", List.of("CLINICIAN"), "TREATMENT",
                null, UUID.randomUUID(), null, null, null,
                "GET", "/v1/patients/1", "GET:/v1/patients/1", "patients", "1",
                0, "sess-1", null, AuthenticationAssurance.none(),
                providerId, null, null, null, subjectId, assuranceLevel,
                null, null, duty);
    }

    /** Every `input.<key>` the policy dereferences. */
    private static Set<String> keysReadByPolicy() throws Exception {
        String rego = Files.readString(REGO);
        Matcher m = Pattern.compile("input\\.([a-z_]+)").matcher(rego);
        Set<String> keys = new java.util.LinkedHashSet<>();
        while (m.find()) {
            keys.add(m.group(1));
        }
        // object.get(input, "x", …) is a dereference the regex above cannot see.
        Matcher g = Pattern.compile("object\\.get\\(input,\\s*\"([a-z_]+)\"").matcher(rego);
        while (g.find()) {
            keys.add(g.group(1));
        }
        return keys;
    }

    @Test
    @DisplayName("the policy file exists where the test expects it — otherwise every assertion is vacuous")
    void policyFileIsReadable() throws Exception {
        assertThat(Files.isRegularFile(REGO))
                .as("authz.rego not found at %s; the contract assertions below would pass vacuously",
                        REGO.toAbsolutePath())
                .isTrue();
        assertThat(keysReadByPolicy())
                .as("parsed no input keys out of authz.rego — the regex is wrong and these tests prove nothing")
                .isNotEmpty();
    }

    @Test
    @DisplayName("every key the mapper emits is a key the policy actually reads")
    void emittedKeysAreConsumedByPolicy() throws Exception {
        Map<String, Object> input = OpaShadowInputMapper.build(
                request("actor-1", "subject-1", "prov-1", "LOA3", DutyContext.absent()),
                PurposeOfUse.TREATMENT, 2, 2, true, 3);

        Set<String> policyKeys = keysReadByPolicy();
        // actor_id is carried for correlation in the decision log rather than read by a rule.
        Set<String> emitted = input.keySet().stream()
                .filter(k -> !k.equals("actor_id"))
                .collect(Collectors.toSet());

        assertThat(policyKeys)
                .as("mapper emits keys the policy never reads — a silent no-op like the identity_loa defect")
                .containsAll(emitted);
    }

    @Test
    @DisplayName("assurance LoA is sent under the key the policy reads, not the old identity_loa")
    void assuranceLoaUsesThePolicysKey() {
        Map<String, Object> input = OpaShadowInputMapper.build(
                request("actor-1", null, null, "LOA3", DutyContext.absent()),
                PurposeOfUse.TREATMENT, null, null, false, 3);

        assertThat(input).containsEntry("assurance_loa", 3);
        assertThat(input)
                .as("identity_loa was the defective key; re-emitting it would silently restore the bug")
                .doesNotContainKey("identity_loa")
                .doesNotContainKey("authentication_aal");
    }

    @Test
    @DisplayName("fields whose vocabulary this service does not share are omitted, not approximated")
    void unmappableFieldsAreAbsent() {
        DutyContext duty = DutyContext.absent();
        Map<String, Object> input = OpaShadowInputMapper.build(
                request("actor-1", "subject-1", "prov-1", "LOA2", duty),
                PurposeOfUse.TREATMENT, null, null, false, 2);

        // access_mode expects an actor zone (WORK|PROFESSIONAL|LIFE); the nearest local value is a
        // WorkMode (CLINICAL_CARE, …). Filling it would leave two rules permanently unmatched
        // while appearing wired — the failure mode this whole class guards against.
        assertThat(input).doesNotContainKey("access_mode");
        // action expects a fine-grained verb; AuthzInternalRequest.action() is "METHOD:path".
        assertThat(input).doesNotContainKey("action");
        assertThat(input).doesNotContainKey("regulated_action");
        assertThat(input).doesNotContainKey("identifier_kind");
    }

    @Test
    @DisplayName("dimensions Java enforces but the policy has no rule for are declared, not silently emitted")
    void policyCoverageGapsAreDeclaredAndNotEmitted() throws Exception {
        Map<String, Object> input = OpaShadowInputMapper.build(
                request("actor-1", null, null, "LOA3", DutyContext.absent()),
                PurposeOfUse.TREATMENT, 2, 2, false, 3);

        // min_aal is supplied by the caller and enforced by PolicyEngine, but the policy defines no
        // MIN_AAL rule. Emitting it would be read by nothing — the identity_loa defect again.
        assertThat(input)
                .as("min_aal is emitted but no policy rule reads it")
                .doesNotContainKey("min_aal");

        Set<String> policyKeys = keysReadByPolicy();
        assertThat(OpaShadowInputMapper.POLICY_COVERAGE_GAPS.keySet())
                .as("a declared coverage gap that the policy DOES read is a stale claim")
                .allSatisfy(gap -> assertThat(policyKeys).doesNotContain(gap));
    }

    @Test
    @DisplayName("the inert-rule register names every policy rule the mapping cannot exercise")
    void inertRegisterMatchesThePolicy() throws Exception {
        String rego = Files.readString(REGO);
        Matcher m = Pattern.compile("deny_reasons contains \"([A-Z_]+)\"").matcher(rego);
        Set<String> declared = new java.util.LinkedHashSet<>();
        while (m.find()) {
            declared.add(m.group(1));
        }
        assertThat(declared).as("parsed no deny reasons — this assertion would be vacuous").isNotEmpty();

        Set<String> accounted = new java.util.LinkedHashSet<>(OpaShadowInputMapper.COMPARABLE_DENY_REASONS);
        accounted.addAll(OpaShadowInputMapper.INERT_DENY_REASONS.keySet());

        assertThat(accounted)
                .as("a policy rule is neither declared comparable nor declared inert, so shadow "
                        + "parity would be claimed over a rule nobody classified")
                .containsAll(declared);
    }

    @Nested
    @DisplayName("divergence attribution")
    class DivergenceAttribution {

        @Test
        @DisplayName("a divergence caused only by unmappable rules is not counted as policy disagreement")
        void unmappableOnlyDivergenceIsNotComparable() {
            assertThat(OpaShadowInputMapper.isComparable(List.of("WORK_REQUIRES_ASSIGNMENT"))).isFalse();
            assertThat(OpaShadowInputMapper.isComparable(List.of("LOGIN_PERSON_FIRST"))).isFalse();
        }

        @Test
        @DisplayName("a divergence citing an exercisable rule is a real disagreement")
        void comparableDivergenceIsCounted() {
            assertThat(OpaShadowInputMapper.isComparable(List.of("MIN_LOA"))).isTrue();
            assertThat(OpaShadowInputMapper.isComparable(List.of("WORK_REQUIRES_ASSIGNMENT", "MIN_LOA"))).isTrue();
        }

        @Test
        @DisplayName("metric reason stays inside the policy vocabulary — no request data becomes a tag")
        void metricReasonIsBounded() {
            assertThat(OpaShadowInputMapper.metricReason(List.of())).isEqualTo("none");
            assertThat(OpaShadowInputMapper.metricReason(List.of("MIN_LOA"))).isEqualTo("MIN_LOA");
            assertThat(OpaShadowInputMapper.metricReason(List.of("patient-4f2a-secret")))
                    .as("an unrecognised reason must collapse to a constant, never become a metric tag")
                    .isEqualTo("other");
        }
    }
}
