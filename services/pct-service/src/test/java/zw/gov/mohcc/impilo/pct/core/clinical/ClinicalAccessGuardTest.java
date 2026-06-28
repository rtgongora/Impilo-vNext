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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalAccessGuardTest {

    @Mock private JourneyRepository journeyRepository;
    @Mock private EncounterRepository encounterRepository;

    private ClinicalAccessGuard guard;
    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        guard = new ClinicalAccessGuard(journeyRepository, encounterRepository);
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
    void noCareContext_allows() {
        // Verify-when-present: a write with no journey/encounter reference is permitted — the
        // facility-team-level RBAC (role + facility + purpose, enforced upstream) is the control.
        assertThatCode(() -> guard.requireCareRelationship(ctx("TREATMENT"), "CPID-1", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void unresolvableCareContext_denies403() {
        // A context WAS supplied (encounter "999") but does not resolve to the subject — denied.
        assertForbidden(() -> guard.requireCareRelationship(ctx("TREATMENT"), "CPID-1", null, "999"));
    }

    @Test
    void emergencyPurpose_bypassesRelationshipRequirement() {
        // Emergency care must never be blocked, even when the referenced context cannot be verified.
        assertThatCode(() -> guard.requireCareRelationship(ctx("EMERGENCY"), "CPID-1", "J-X", null))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.requireCareRelationship(ctx("BREAK_GLASS"), "CPID-1", "J-X", null))
                .doesNotThrowAnyException();
    }
}
