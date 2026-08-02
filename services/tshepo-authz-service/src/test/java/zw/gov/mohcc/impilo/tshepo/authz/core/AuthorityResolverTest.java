package zw.gov.mohcc.impilo.tshepo.authz.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.AuthorityBinding;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The doctrinal invariant this whole checkpoint turns on: <strong>a work context does not create
 * authority</strong>. Holding a validated duty token proves where an actor is operating; it does
 * not prove they may act there.
 */
class AuthorityResolverTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

    /** present, active, tokenKind, actorId, facility, workspace, dept, ward, prog, org, provider,
     *  role, assignment, jurisdiction, workMode, clinicalDataAccess, contextId, servicePoint */
    private static DutyContext duty(String assignmentId, String providerId, String role) {
        return new DutyContext(true, true, "WORK_CONTEXT", "actor-1",
                "fac-1", "ws-1", "dept-1", "ward-1", "prog-1",
                "org-1", providerId, role, assignmentId, "NATIONAL",
                "CLINICAL_CARE", "IDENTIFIED", "ctx-1", "sp-1");
    }

    private static AuthzInternalRequest request(DutyContext duty, List<String> claimedRoles) {
        return new AuthzInternalRequest(
                UUID.randomUUID(), "actor-1", "PROVIDER", claimedRoles, "TREATMENT",
                null, UUID.randomUUID(), null, null, null,
                "POST", "/v1/patients/1/notes", "POST:/v1/patients/1/notes", "patients", "1",
                0, "sess-1", null, AuthenticationAssurance.none(),
                "prov-hdr", null, null, null, "cpid-1", "LOA3",
                null, null, duty);
    }

    @Test
    @DisplayName("A CONTEXT WITH NO APPOINTMENT AUTHORISES NOTHING — the core invariant")
    void contextAloneIsNotAuthority() {
        // A perfectly valid, introspected, active WORK_CONTEXT token naming a facility, a
        // workspace, a ward and a work mode -- but no appointment. The actor has demonstrably
        // *entered a workplace*. That is not a grant of authority to act in it.
        AuthorityBinding b = AuthorityResolver.resolve(
                request(duty(null, "prov-1", "NURSE"), List.of("NURSE")), false);

        assertThat(b.authorityId()).isNull();
        assertThat(AuthorityResolver.isActionable(b, NOW))
                .as("selecting a workplace must never create authority within it")
                .isFalse();
        assertThat(AuthorityResolver.metricLabel(b, NOW)).isEqualTo("no_appointment");
    }

    @Test
    @DisplayName("claimed roles on the request never become authority")
    void claimedRolesAreNotAuthority() {
        // No usable duty token at all, but the request asserts a powerful role set. A role is a
        // claim; authority is an activated right.
        AuthorityBinding b = AuthorityResolver.resolve(
                request(DutyContext.absent(), List.of("SYSTEM_ADMIN", "CLINICIAN")), false);

        assertThat(b).isEqualTo(AuthorityResolver.NONE);
        assertThat(b.roles()).isEmpty();
        assertThat(AuthorityResolver.isActionable(b, NOW)).isFalse();
    }

    @Test
    @DisplayName("an appointment on a usable context IS actionable")
    void appointmentConfersAuthority() {
        AuthorityBinding b = AuthorityResolver.resolve(
                request(duty("assign-9", "prov-1", "NURSE"), List.of("NURSE")), false);

        assertThat(b.authorityId()).isEqualTo("assign-9");
        assertThat(b.appointmentId()).isEqualTo("assign-9");
        assertThat(b.licenceId()).isEqualTo("prov-1");
        assertThat(b.roles()).containsExactly("NURSE");
        assertThat(AuthorityResolver.isActionable(b, NOW))
                .as("a resolver that can never authorise proves nothing about the invariant")
                .isTrue();
    }

    @Test
    @DisplayName("a suspended provider holds an appointment and still may not act")
    void suspensionRemovesAuthority() {
        AuthorityBinding b = AuthorityResolver.resolve(
                request(duty("assign-9", "prov-1", "NURSE"), List.of("NURSE")), true);

        assertThat(b.authorityId()).isEqualTo("assign-9");
        assertThat(b.suspended()).isTrue();
        assertThat(AuthorityResolver.isActionable(b, NOW)).isFalse();
        assertThat(AuthorityResolver.metricLabel(b, NOW)).isEqualTo("suspended");
    }

    @Test
    @DisplayName("a revoked duty token yields no authority even though one was presented")
    void revokedTokenYieldsNothing() {
        assertThat(AuthorityResolver.resolve(request(DutyContext.revoked(), List.of("NURSE")), false))
                .isEqualTo(AuthorityResolver.NONE);
    }

    @Test
    @DisplayName("roles come from the validated token, not from the request's own claim")
    void rolesComeFromTheToken() {
        // The request claims SYSTEM_ADMIN; the token says NURSE. The token wins, because it is
        // the only one of the two that anything validated.
        AuthorityBinding b = AuthorityResolver.resolve(
                request(duty("assign-9", "prov-1", "NURSE"), List.of("SYSTEM_ADMIN")), false);

        assertThat(b.roles()).containsExactly("NURSE");
        assertThat(b.roles()).doesNotContain("SYSTEM_ADMIN");
    }

    @Test
    @DisplayName("expiry is honoured where present — and is currently never populated, by design")
    void expiryIsHonouredButUnavailable() {
        AuthorityBinding b = AuthorityResolver.resolve(
                request(duty("assign-9", "prov-1", "NURSE"), List.of("NURSE")), false);

        // The duty token carries no appointment/licence expiry, so this stays null rather than
        // being faked with a placeholder that would make isExpired() a check that cannot fire.
        assertThat(b.expiresAt())
                .as("a fabricated expiry would be a control that cannot fail")
                .isNull();

        // The mechanism itself works when an expiry IS known.
        AuthorityBinding expired = AuthorityBinding.of("WORK", "assign-9", List.of("NURSE"),
                "prov-1", "assign-9", null, NOW.minusSeconds(60), false);
        assertThat(AuthorityResolver.isActionable(expired, NOW)).isFalse();
        assertThat(AuthorityResolver.metricLabel(expired, NOW)).isEqualTo("expired");
    }
}
