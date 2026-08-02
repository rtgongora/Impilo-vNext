package zw.gov.mohcc.impilo.tshepo.authz.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Operating context must come from the duty token, never from the caller.
 *
 * <p>Before this existed the PDP regenerated identity but not context, so a browser's
 * {@code x-facility-id} reached every service exactly as it was set. These tests pin the two
 * halves of the fix: what can be regenerated is regenerated from validated evidence, and what
 * cannot be regenerated is left alone rather than deleted.</p>
 */
class ContextHeaderAuthorityTest {

    private static final UUID CLIENT_FACILITY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENT_WORKSPACE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static DutyContext duty(String facility, String workspace, String department) {
        // present, active, tokenKind, actorId, then the context claims. usable() requires
        // tokenKind == WORK_CONTEXT, so an introspected-but-wrong-kind token is not context.
        return new DutyContext(true, true, "WORK_CONTEXT", "actor-1",
                facility, workspace, department, "ward-9", "prog-3",
                "org-1", "prov-1", "NURSE", "assign-1", "NATIONAL",
                "CLINICAL_CARE", "IDENTIFIED", "ctx-1", "sp-1");
    }

    private static AuthzInternalRequest request(DutyContext duty, String purposeHeader) {
        return new AuthzInternalRequest(
                UUID.randomUUID(), "actor-1", "PROVIDER", List.of("NURSE"), purposeHeader,
                null, UUID.randomUUID(), CLIENT_FACILITY, CLIENT_WORKSPACE, "shift-7",
                "GET", "/v1/patients/1", "GET:/v1/patients/1", "patients", "1",
                0, "sess-1", null, AuthenticationAssurance.none(),
                "prov-client", "dept-client", "ward-client", "prog-client", "subject-1", "LOA3",
                null, "IN_PROGRESS", duty);
    }

    @Test
    @DisplayName("validated context comes from the duty token, not the caller's headers")
    void contextComesFromDutyToken() {
        Map<String, String> headers = ContextHeaderAuthority.validatedContext(
                request(duty("facility-TOKEN", "workspace-TOKEN", "dept-TOKEN"), "TREATMENT"),
                PurposeOfUse.TREATMENT);

        assertThat(headers)
                .as("the caller sent %s; the token says facility-TOKEN", CLIENT_FACILITY)
                .containsEntry(TrustHeaders.FACILITY_ID, "facility-TOKEN")
                .containsEntry(TrustHeaders.WORKSPACE_ID, "workspace-TOKEN")
                .containsEntry(TrustHeaders.DEPARTMENT_ID, "dept-TOKEN")
                .containsEntry(TrustHeaders.PURPOSE_OF_USE, "TREATMENT");
        assertThat(headers.get(TrustHeaders.FACILITY_ID)).isNotEqualTo(CLIENT_FACILITY.toString());
    }

    @Test
    @DisplayName("no usable duty token yields no context at all — an unvalidated facility is not a facility")
    void noDutyTokenYieldsNoContext() {
        Map<String, String> headers = ContextHeaderAuthority.validatedContext(
                request(DutyContext.absent(), "TREATMENT"), PurposeOfUse.TREATMENT);

        // The caller supplied a facility and a workspace. Neither survives, because nothing
        // validated them. This is the whole point of the control, and the reason it is staged.
        assertThat(headers).doesNotContainKey(TrustHeaders.FACILITY_ID);
        assertThat(headers).doesNotContainKey(TrustHeaders.WORKSPACE_ID);
        assertThat(headers).containsEntry(TrustHeaders.PURPOSE_OF_USE, "TREATMENT");
    }

    @Test
    @DisplayName("a revoked duty token is not a source of context")
    void revokedDutyTokenYieldsNoContext() {
        Map<String, String> headers = ContextHeaderAuthority.validatedContext(
                request(DutyContext.revoked(), "TREATMENT"), PurposeOfUse.TREATMENT);
        assertThat(headers).doesNotContainKey(TrustHeaders.FACILITY_ID);
    }

    @Test
    @DisplayName("purpose is normalised to the validated enum, not echoed from the header")
    void purposeIsTheValidatedEnum() {
        Map<String, String> headers = ContextHeaderAuthority.validatedContext(
                request(DutyContext.absent(), "treatment; drop table"), PurposeOfUse.TREATMENT);
        assertThat(headers).containsEntry(TrustHeaders.PURPOSE_OF_USE, "TREATMENT");
    }

    @Test
    @DisplayName("headers with no validated source are never claimed as regenerated")
    void unregeneratedHeadersAreDeclaredAndNotEmitted() {
        Map<String, String> headers = ContextHeaderAuthority.validatedContext(
                request(duty("f", "w", "d"), "TREATMENT"), PurposeOfUse.TREATMENT);

        // shift and workflow-state have no duty claim; the work-context token is a credential.
        // Emitting any of them would mean echoing the client's own value back as if validated.
        assertThat(headers).doesNotContainKey(TrustHeaders.SHIFT_ID);
        assertThat(headers).doesNotContainKey(TrustHeaders.WORKFLOW_STATE);
        assertThat(headers).doesNotContainKey(TrustHeaders.WORK_CONTEXT_TOKEN);

        assertThat(ContextHeaderAuthority.UNREGENERATED_HEADERS)
                .containsKeys(TrustHeaders.SHIFT_ID, TrustHeaders.WORKFLOW_STATE,
                        TrustHeaders.WORK_CONTEXT_TOKEN);
        assertThat(ContextHeaderAuthority.REGENERATED_HEADERS)
                .doesNotContainAnyElementsOf(ContextHeaderAuthority.UNREGENERATED_HEADERS.keySet());
    }

    @Test
    @DisplayName("divergence is detected when the caller claims a different facility than the token")
    void divergenceIsDetected() {
        var diffs = ContextHeaderAuthority.compare(
                request(duty("facility-TOKEN", "workspace-TOKEN", "dept-TOKEN"), "TREATMENT"),
                PurposeOfUse.TREATMENT);

        var facility = diffs.stream()
                .filter(d -> d.header().equals(TrustHeaders.FACILITY_ID)).findFirst().orElseThrow();
        assertThat(facility.differs()).isTrue();
        assertThat(facility.clientSupplied()).isTrue();
        assertThat(facility.validatedPresent()).isTrue();
    }

    @Test
    @DisplayName("a caller-only claim is reported distinctly — it is the claim AUTHORITATIVE will drop")
    void clientOnlyClaimIsDistinguished() {
        var diffs = ContextHeaderAuthority.compare(
                request(DutyContext.absent(), "TREATMENT"), PurposeOfUse.TREATMENT);

        var facility = diffs.stream()
                .filter(d -> d.header().equals(TrustHeaders.FACILITY_ID)).findFirst().orElseThrow();
        assertThat(facility.clientSupplied()).isTrue();
        assertThat(facility.validatedPresent()).isFalse();
        assertThat(facility.differs())
                .as("client-only is not a mismatch; conflating them hides which claims get dropped")
                .isFalse();
    }

    @Test
    @DisplayName("mode parsing refuses a typo rather than degrading to PASSTHROUGH")
    void modeTypoIsRefused() {
        assertThat(ContextHeaderAuthority.Mode.parse(null)).isEqualTo(ContextHeaderAuthority.Mode.PASSTHROUGH);
        assertThat(ContextHeaderAuthority.Mode.parse("")).isEqualTo(ContextHeaderAuthority.Mode.PASSTHROUGH);
        assertThat(ContextHeaderAuthority.Mode.parse("shadow")).isEqualTo(ContextHeaderAuthority.Mode.SHADOW);
        assertThat(ContextHeaderAuthority.Mode.parse("AUTHORITATIVE"))
                .isEqualTo(ContextHeaderAuthority.Mode.AUTHORITATIVE);

        // Degrading to PASSTHROUGH would silently disable the control while config claims it is on.
        assertThatThrownBy(() -> ContextHeaderAuthority.Mode.parse("AUTHORITATIVE_"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected PASSTHROUGH, SHADOW or AUTHORITATIVE");
    }
}
