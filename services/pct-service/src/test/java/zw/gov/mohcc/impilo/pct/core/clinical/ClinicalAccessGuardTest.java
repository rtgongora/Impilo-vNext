package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantCheck;
import zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantClient;
import zw.gov.mohcc.impilo.sharedkernel.security.UngovernedOverride;
import zw.gov.mohcc.impilo.sharedkernel.security.UngovernedOverrideRecorder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalAccessGuardTest {

    @Mock private JourneyRepository journeyRepository;
    @Mock private EncounterRepository encounterRepository;
    @Mock private BreakGlassGrantClient grantClient;
    @Mock private UngovernedOverrideRecorder overrideRecorder;

    private ClinicalAccessGuard guard;
    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        guard = new ClinicalAccessGuard(journeyRepository, encounterRepository,
                grantClient, overrideRecorder);
        // Default: the actor holds a real grant. An emergency purpose-of-use alone no longer
        // waives the care-relationship requirement.
        lenient().when(grantClient.check(any()))
                .thenReturn(BreakGlassGrantCheck.valid("BREAK_GLASS_REQUEST", "grant-test"));
    }

    private TrustContext ctx(String purpose) {
        return new TrustContext(TENANT, "provider-1", "PROVIDER", purpose, null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);
    }

    private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void journeyBelongingToSubject_allows() {
        JourneyEntity j = new JourneyEntity();
        j.setJourneyId("J-1");
        j.setPatientCpid("CPID-1");
        when(journeyRepository.findByTenantIdAndJourneyId(TENANT, "J-1")).thenReturn(Optional.of(j));

        assertThatCode(() -> guard.requireCareRelationship(ctx("TREATMENT"), "CPID-1", "J-1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void journeyForDifferentPatient_denies403() {
        JourneyEntity j = new JourneyEntity();
        j.setJourneyId("J-1");
        j.setPatientCpid("CPID-OTHER");
        when(journeyRepository.findByTenantIdAndJourneyId(TENANT, "J-1")).thenReturn(Optional.of(j));

        // Provider references a journey that belongs to a DIFFERENT patient than the write subject.
        assertForbidden(() -> guard.requireCareRelationship(ctx("TREATMENT"), "CPID-1", "J-1", null));
    }

    @Test
    void encounterBelongingToSubject_allows() {
        EncounterEntity e = new EncounterEntity();
        e.setSubjectCpid("CPID-1");
        when(encounterRepository.findByTenantIdAndId(TENANT, 42L)).thenReturn(Optional.of(e));

        assertThatCode(() -> guard.requireCareRelationship(ctx("TREATMENT"), "CPID-1", null, "42"))
                .doesNotThrowAnyException();
    }

    @Test
    void noCareContext_denies403() {
        // Strict: no journey and no encounter referenced — cannot establish a care relationship.
        assertForbidden(() -> guard.requireCareRelationship(ctx("TREATMENT"), "CPID-1", null, null));
    }

    @Test
    void unresolvableCareContext_denies403() {
        // A context WAS supplied (encounter "999") but does not resolve to the subject — denied.
        assertForbidden(() -> guard.requireCareRelationship(ctx("TREATMENT"), "CPID-1", null, "999"));
    }

    // ════════════════════════════════════════════════════════════════════
    // The emergency waiver, decided by the grant rather than by the purpose string
    // ════════════════════════════════════════════════════════════════════

    @Test
    void emergencyPurposeWithValidGrant_waivesRelationshipRequirement() {
        // Emergency care must never be blocked when the emergency is real.
        assertThatCode(() -> guard.requireCareRelationship(ctx("EMERGENCY"), "CPID-1", null, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.requireCareRelationship(ctx("BREAK_GLASS"), "CPID-1", null, null))
                .doesNotThrowAnyException();
        verifyNoInteractions(overrideRecorder);
    }

    @Test
    void emergencyPurposeWithNoGrant_denies403() {
        when(grantClient.check(any())).thenReturn(BreakGlassGrantCheck.noGrant());

        // The exposure closing: setting X-Purpose-Of-Use: BREAK_GLASS used to be enough to mint a
        // clinical record against an arbitrary CPID. It is not any more.
        assertForbidden(() -> guard.requireCareRelationship(ctx("BREAK_GLASS"), "CPID-1", null, null));
        verify(overrideRecorder, never()).record(any());
    }

    @Test
    void emergencyPurposeWhenTrustPlaneUnreachable_waivesAndRecordsAnUngovernedOverride() {
        when(grantClient.check(any()))
                .thenReturn(BreakGlassGrantCheck.unreachable("connect timed out after 2000ms"));

        assertThatCode(() -> guard.requireCareRelationship(ctx("EMERGENCY"), "CPID-1", null, null))
                .doesNotThrowAnyException();

        // Asserting the waiver alone would pass even if the override left no trace, which is the
        // failure this mechanism exists to prevent. Assert the record.
        var captor = org.mockito.ArgumentCaptor.forClass(UngovernedOverride.class);
        verify(overrideRecorder).record(captor.capture());
        assertThat(captor.getValue().actorId()).isEqualTo("provider-1");
        assertThat(captor.getValue().subjectRef()).isEqualTo("CPID-1");
        assertThat(captor.getValue().action()).isEqualTo("CLINICAL_WRITE_WITHOUT_CARE_CONTEXT");
        assertThat(captor.getValue().reason()).contains("timed out");
        assertThat(captor.getValue().reviewObligation()).isNotBlank();
    }

    @Test
    void emergencyPurposeWhenRecorderCannotWrite_refusesRatherThanWaivingSilently() {
        when(grantClient.check(any())).thenReturn(BreakGlassGrantCheck.unreachable("no route to host"));
        org.mockito.Mockito.doThrow(new IllegalStateException("outbox write failed"))
                .when(overrideRecorder).record(any());

        assertThatThrownBy(() -> guard.requireCareRelationship(ctx("EMERGENCY"), "CPID-1", null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusedAndCouldNotAskReachOppositeOutcomes() {
        when(grantClient.check(any())).thenReturn(BreakGlassGrantCheck.noGrant());
        assertForbidden(() -> guard.requireCareRelationship(ctx("EMERGENCY"), "CPID-1", null, null));

        when(grantClient.check(any())).thenReturn(BreakGlassGrantCheck.unreachable("connection refused"));
        assertThatCode(() -> guard.requireCareRelationship(ctx("EMERGENCY"), "CPID-1", null, null))
                .doesNotThrowAnyException();

        // Same purpose, same actor, same absent care context — opposite outcomes, decided only by
        // whether the trust plane answered. Collapsing these would re-run the original defect.
        verify(overrideRecorder).record(any());
    }
}
