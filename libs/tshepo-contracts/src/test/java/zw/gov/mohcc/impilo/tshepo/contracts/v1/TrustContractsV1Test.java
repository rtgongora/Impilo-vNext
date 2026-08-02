package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.adapter.AuthzResponseChallengeAdapter;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.adapter.LegacyAuthenticationAssuranceAdapter;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.adapter.LegacyConsentDecisionAdapter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustContractsV1Test {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void deterministicSerializationAndVersioning() throws Exception {
        IdentityAssurance ial = IdentityAssurance.of(2, "IN-PERSON", "ev-1");
        String json1 = mapper.writeValueAsString(ial);
        String json2 = mapper.writeValueAsString(ial);
        assertThat(json1).isEqualTo(json2);
        assertThat(json1).contains("\"contractVersion\":\"tshepo.trust.v1\"");
        IdentityAssurance roundTrip = mapper.readValue(json1, IdentityAssurance.class);
        assertThat(roundTrip).isEqualTo(ial);
    }

    @Test
    void rejectsMalformedCriticalValues() {
        assertThatThrownBy(() -> IdentityAssurance.of(9, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuthenticationAssurance.of(
                4, List.of(), null, null, false, null, null, RecoveryAuthenticationState.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrustChallengeOutcome(
                "tshepo.trust.v0",
                TrustChallengeDecision.ALLOW,
                null, null, null, null, List.of(), List.of(),
                null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void separatesAalFromIal() {
        IdentityAssurance ial = IdentityAssurance.of(3, "BIOMETRIC", null);
        AuthenticationAssurance aal = AuthenticationAssurance.of(
                1, List.of("pwd"), Instant.parse("2026-08-01T00:00:00Z"), null, false,
                "s1", "f1", RecoveryAuthenticationState.NONE);
        assertThat(ial.ial()).isEqualTo(3);
        assertThat(aal.aal()).isEqualTo(1);
        assertThat(ial.contractVersion()).isEqualTo(aal.contractVersion());
    }

    @Test
    void separatesWorkloadFromHuman() {
        HumanSubjectRef human = HumanSubjectRef.of("HID-1", "PROVIDER", "PRV-1");
        WorkloadSubject workload = WorkloadSubject.of("wl-bff", "experience-bff", "default", "impilo-backend", "bff");
        assertThat(human.healthId()).isNotEqualTo(workload.workloadId());
        TrustDecisionInput input = TrustDecisionInput.builder()
                .humanSubject(human)
                .callingWorkload(workload)
                .destination(ServiceRequestContext.of("pct-service", "GET", "/v1/x", "READ", "Patient", "p1", "CLINICAL"))
                .identityAssurance(IdentityAssurance.of(2, null, null))
                .authenticationAssurance(AuthenticationAssurance.none())
                .operatingMode(OperatingMode.NORMAL)
                .build();
        assertThat(input.humanSubject().healthId()).isEqualTo("HID-1");
        assertThat(input.callingWorkload().workloadId()).isEqualTo("wl-bff");
    }

    @Test
    void rejectsClientHeaderPopulationForAuthoritativeFields() {
        assertThatThrownBy(() -> TrustDecisionInput.rejectClientHeaderPopulation(
                Map.of("x-actor-id", "spoofed")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constrainedRecoveryIsNotOrdinaryWorkforceAal2() {
        AuthenticationAssurance recovery = AuthenticationAssurance.of(
                2,
                List.of("pwd", "auth-recovery-authn-code-form"),
                Instant.parse("2026-08-01T00:00:00Z"),
                null,
                false,
                "sess",
                "flow",
                RecoveryAuthenticationState.CONSTRAINED_RECOVERY);
        assertThat(recovery.aal()).isEqualTo(2);
        assertThat(recovery.grantsOrdinaryWorkforceAal2()).isFalse();
        assertThat(recovery.isConstrainedRecovery()).isTrue();
    }

    @Test
    void delegationCannotIncreaseAuthority() {
        Instant exp = Instant.parse("2026-08-01T12:00:00Z");
        DelegatedHumanContext parent = baseDelegation(List.of("READ"), List.of("clinical.read"), 0, exp, 2);
        DelegatedHumanContext widerActions = baseDelegation(List.of("READ", "WRITE"), List.of("clinical.read"), 1, exp, 2);
        DelegatedHumanContext higherAal = baseDelegation(List.of("READ"), List.of("clinical.read"), 1, exp, 3);
        DelegatedHumanContext laterExpiry = baseDelegation(
                List.of("READ"), List.of("clinical.read"), 1, Instant.parse("2026-08-02T12:00:00Z"), 2);
        assertThat(parent.permitsDownstream(widerActions)).isFalse();
        assertThat(parent.permitsDownstream(higherAal)).isFalse();
        assertThat(parent.permitsDownstream(laterExpiry)).isFalse();

        DelegatedHumanContext ok = baseDelegation(List.of("READ"), List.of("clinical.read"), 1, exp, 2);
        assertThat(parent.permitsDownstream(ok)).isTrue();
        assertThatThrownBy(() -> DelegationChain.of(List.of(parent, widerActions), 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void challengeSerializationIsSafe() throws Exception {
        assertThatThrownBy(() -> TrustChallengeOutcome.deny(
                "Bearer eyJhbGciOiJIUzI1NiJ9.aaaaaaaaaa.bbbbbbbbbb", "msg", "d1"))
                .isInstanceOf(IllegalArgumentException.class);
        TrustChallengeOutcome ok = TrustChallengeOutcome.deny("CONSENT_REQUIRED", "trust.consent.required", "d1");
        assertThat(ok.decision()).isEqualTo(TrustChallengeDecision.DENY);
        String json = mapper.writeValueAsString(ok);
        assertThat(json).contains("\"reason_code\"");
        assertThat(json).contains("\"user_message_key\"");
        assertThat(json).contains("\"decision_id\"");
        assertThat(json).doesNotContain("errorMessage");
    }

    @Test
    void consentAndLawfulBasisAreSeparate() {
        ConsentDecision consent = ConsentDecision.permit("c1", List.of("read"), "subj", "TREATMENT");
        LawfulBasisDecision basis = LawfulBasisDecision.permit(
                LawfulBasisType.DIRECT_CARE_RELATIONSHIP, "appt-1", "subj", "TREATMENT");
        assertThat(consent.permitted()).isTrue();
        assertThat(basis.basisType()).isEqualTo(LawfulBasisType.DIRECT_CARE_RELATIONSHIP);
        assertThatThrownBy(() -> LawfulBasisDecision.permit(
                LawfulBasisType.EXPLICIT_CONSENT, "c1", "subj", "TREATMENT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void asyncReferenceRejectsAccessTokens() {
        WorkloadSubject worker = WorkloadSubject.of("jobs", "jobs-service", "default", "jobs", "jobs");
        assertThatThrownBy(() -> AsyncExecutionReference.of(
                "eyJhbGciOiJIUzI1NiJ9.aaaaaaaaaaaaaaaaaaaa.bbbbbbbbbbbbbbbbbbbb",
                worker,
                HumanSubjectRef.of("HID-1", "PROVIDER", null),
                List.of("PROCESS"),
                List.of("task"),
                "jobs",
                "SYSTEM",
                1,
                Instant.parse("2026-08-01T12:00:00Z"),
                false,
                "corr-1"))
                .isInstanceOf(IllegalArgumentException.class);

        AsyncExecutionReference ref = AsyncExecutionReference.of(
                "opaque-task-ref-9f3a",
                worker,
                HumanSubjectRef.of("HID-1", "PROVIDER", null),
                List.of("PROCESS"),
                List.of("task"),
                "jobs",
                "SYSTEM",
                1,
                Instant.parse("2026-08-01T12:00:00Z"),
                false,
                "corr-1");
        assertThat(ref.containsAccessTokenMaterial()).isFalse();
        assertThat(ref.isExecutable(Instant.parse("2026-08-01T11:00:00Z"))).isTrue();
    }

    @Test
    void auditEventExcludesSecretsAndClinicalPayloads() {
        assertThatThrownBy(() -> new TrustAuditEvent(
                TrustContractVersion.V1,
                "evt-1",
                Instant.parse("2026-08-01T00:00:00Z"),
                "req",
                "tr",
                "dec",
                "wl",
                "dst",
                "actor",
                null,
                null,
                "TREATMENT",
                "READ",
                "res",
                "jwt",
                2,
                2,
                RecoveryAuthenticationState.NONE,
                "p1",
                "bff",
                "ALLOW",
                Map.of("access_token", "secret")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new TrustAuditEvent(
                TrustContractVersion.V1,
                "evt-1",
                Instant.parse("2026-08-01T00:00:00Z"),
                "req",
                "tr",
                "dec",
                "wl",
                "dst",
                "actor",
                null,
                null,
                "TREATMENT",
                "READ",
                "res",
                "jwt",
                2,
                2,
                RecoveryAuthenticationState.NONE,
                "p1",
                "bff",
                "ALLOW",
                Map.of("clinical_note", "fever")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adaptersPreserveBehaviourWithoutBroadeningAuthority() {
        zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance legacyAal =
                new zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance(
                        2,
                        List.of("pwd", "auth-recovery-authn-code-form"),
                        Instant.parse("2026-08-01T00:00:00Z"),
                        null,
                        false,
                        "s",
                        "f");
        AuthenticationAssurance canonical = LegacyAuthenticationAssuranceAdapter.toCanonical(legacyAal);
        assertThat(canonical.isConstrainedRecovery()).isTrue();
        assertThat(canonical.grantsOrdinaryWorkforceAal2()).isFalse();

        zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance back =
                LegacyAuthenticationAssuranceAdapter.toLegacy(canonical);
        assertThat(back.aal()).isLessThanOrEqualTo(1);
        assertThat(back.methods()).contains("auth-recovery-authn-code-form");

        AuthzResponse legacy = AuthzResponse.stepUp(List.of("totp"), 10);
        TrustChallengeOutcome challenge = AuthzResponseChallengeAdapter.toCanonical(legacy, "d1", "pv1");
        assertThat(challenge.decision()).isEqualTo(TrustChallengeDecision.STEP_UP_REQUIRED);
        AuthzResponse round = AuthzResponseChallengeAdapter.toLegacy(challenge);
        assertThat(round.verdict()).isEqualTo(Verdict.STEP_UP_REQUIRED);

        TrustChallengeOutcome recoveryRequired = TrustChallengeOutcome.of(
                TrustChallengeDecision.RECOVERY_REQUIRED,
                "RECOVERY_REQUIRED",
                "trust.recovery.required",
                "ENROLL_FACTOR",
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                "d2",
                null,
                null,
                null);
        assertThatThrownBy(() -> AuthzResponseChallengeAdapter.toLegacy(recoveryRequired))
                .isInstanceOf(UnsupportedOperationException.class);

        zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision legacyConsent =
                zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision.permit("c1", List.of("read"));
        ConsentDecision v1 = LegacyConsentDecisionAdapter.toCanonical(legacyConsent, "subj", "TREATMENT");
        assertThat(v1.subjectRef()).isEqualTo("subj");
        assertThat(LegacyConsentDecisionAdapter.toLegacy(v1).permitted()).isTrue();
    }

    @Test
    void expiryAndDelegationDepthHandled() {
        Instant now = Instant.parse("2026-08-01T10:00:00Z");
        WorkloadAssurance wa = WorkloadAssurance.of(
                "client_credentials",
                Instant.parse("2026-08-01T09:00:00Z"),
                Instant.parse("2026-08-01T09:30:00Z"),
                false,
                "keycloak");
        assertThat(wa.isExpired(now)).isTrue();
        assertThatThrownBy(() -> DelegationChain.of(List.of(
                baseDelegation(List.of("READ"), List.of("a"), 0, now, 2),
                baseDelegation(List.of("READ"), List.of("a"), 1, now, 2),
                baseDelegation(List.of("READ"), List.of("a"), 2, now, 2)
        ), 2)).isInstanceOf(IllegalArgumentException.class);
    }

    private static DelegatedHumanContext baseDelegation(
            List<String> actions, List<String> scopes, int depth, Instant expires, int aal) {
        return new DelegatedHumanContext(
                TrustContractVersion.V1,
                HumanSubjectRef.of("HID-1", "PROVIDER", "PRV-1"),
                ActiveContext.of("t1", "f1", null, null, null, null, null, null, "wct-1"),
                AuthorityBinding.of("APPOINTMENT", "a1", List.of("CLINICIAN"), null, "appt", null, expires, false),
                "TREATMENT",
                "pct-service",
                actions,
                scopes,
                AuthenticationAssurance.of(
                        aal, List.of("totp"), Instant.parse("2026-08-01T00:00:00Z"), null, false,
                        "s", "f", RecoveryAuthenticationState.NONE),
                depth,
                expires,
                "corr",
                "req");
    }
}
