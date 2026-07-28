package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.persistence.entity.TopAuthorisationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.TopProcedureEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.TopAuthorisationRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.TopProcedureRepository;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Termination service behaviour, and the structural authorisation guarantee stated in code.
 *
 * <p>The load-bearing invariant — a procedure can only reference an AUTHORISED authorisation, and
 * that authorisation cannot leave AUTHORISED while referenced — is enforced by the composite FK in
 * V435 and mutation-proven against real Postgres in the migration probe (6 PASS, 5 rejected). This
 * unit test proves the two things the service itself owns: that {@code recordProcedure} pins the
 * procedure's status to AUTHORISED (so the FK target is the authorised row, never a coincidentally
 * matching non-authorised one), and — by reflection — that neither TOP entity carries an
 * event/outbox field, the code-side twin of the no-record-level-emit guard.
 */
class TerminationServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private TopAuthorisationRepository authorisations;
    private TopProcedureRepository procedures;
    private TerminationService service;

    @BeforeEach
    void setUp() {
        authorisations = mock(TopAuthorisationRepository.class);
        procedures = mock(TopProcedureRepository.class);
        service = new TerminationService(authorisations, procedures, inertConfidentiality());
        when(authorisations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(procedures.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static TopAuthorisationEntity authorised() {
        TopAuthorisationEntity a = new TopAuthorisationEntity();
        a.setTenantId(TENANT);
        a.setSubjectCpid("CPID-MOTHER");
        a.setStatus(TopAuthorisationEntity.STATUS_AUTHORISED);
        a.setLegalGround("LIFE_OF_MOTHER");
        a.setConsentGiven("GIVEN");
        a.setRecordedBy("clinician");
        return a;
    }

    @Test
    @DisplayName("an authorisation is minted an id and defaults consent to NOT_RECORDED when unset")
    void authoriseDefaults() {
        TopAuthorisationEntity a = new TopAuthorisationEntity();
        a.setTenantId(TENANT);
        a.setSubjectCpid("CPID-MOTHER");
        a.setStatus(TopAuthorisationEntity.STATUS_REQUESTED);
        a.setLegalGround("MENTAL_HEALTH");
        a.setRecordedBy("clinician");

        var saved = service.authorise(a);
        assertThat(saved.getAuthorisationId()).isNotNull();
        assertThat(saved.getRecordedAt()).isNotNull();
        // A null consent never reads as an affirmative; it reads as not-yet-recorded.
        assertThat(saved.getConsentGiven()).isEqualTo("NOT_RECORDED");
        assertThat(saved.getSensitivityClass()).isEqualTo("FULL_CLINICAL");
    }

    @Test
    @DisplayName("recordProcedure pins authorisation_status to AUTHORISED regardless of caller input")
    void recordProcedurePinsStatus() {
        TopProcedureEntity p = new TopProcedureEntity();
        p.setTenantId(TENANT);
        p.setSubjectCpid("CPID-MOTHER");
        p.setAuthorisationId(UUID.randomUUID());
        // A caller trying to slip a non-authorised status in is overwritten, so the composite FK can
        // only ever resolve against the AUTHORISED row.
        p.setAuthorisationStatus("REQUESTED");
        p.setMethod("MEDICAL");
        p.setPerformedOn(LocalDate.now());
        p.setRecordedBy("clinician");

        var saved = service.recordProcedure(p);
        assertThat(saved.getProcedureId()).isNotNull();
        assertThat(saved.getAuthorisationStatus()).isEqualTo(TopAuthorisationEntity.STATUS_AUTHORISED);
    }

    @Test
    @DisplayName("neither TOP entity carries an event/outbox field — aggregate-only, made structural")
    void noEmitField() {
        // The code-side twin of check-top-no-record-level-emit.sh: even the shape of the entity must
        // give a future emit path nothing to latch onto.
        for (Class<?> c : new Class<?>[]{TopAuthorisationEntity.class, TopProcedureEntity.class}) {
            for (Field f : c.getDeclaredFields()) {
                String name = f.getName().toLowerCase();
                assertThat(name)
                        .as("%s must declare no event/outbox field; found %s", c.getSimpleName(), f.getName())
                        .doesNotContain("outbox", "event", "emit", "publish", "topic");
            }
        }
    }

    /**
     * A confidentiality provider that resolves nothing: no policy, no demographics. The stamper then
     * records POLICY_UNAVAILABLE and leaves the class at FULL_CLINICAL — which is exactly the
     * stamp-fails-open behaviour these tests should see by default.
     */
    private static ConfidentialCarePolicyProvider inertConfidentiality() {
        ConfidentialCarePolicyProvider p = mock(ConfidentialCarePolicyProvider.class);
        when(p.classStampingEnabled()).thenReturn(false);
        when(p.policyAsOf(any(), any())).thenReturn(new zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialCarePolicy() {
            public java.util.Optional<Integer> confidentialFromGuardianAgeYears(
                    zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialityCategory c) {
                return java.util.Optional.empty();
            }
            public java.util.Optional<Boolean> nonTerminationLossConfidentialFromGuardian() {
                return java.util.Optional.empty();
            }
            public String contentVersion() { return null; }
            public boolean ratified() { return false; }
        });
        when(p.subjectAt(any(), any(), any(), any()))
                .thenReturn(new ConfidentialityStamper.SubjectContext(null, false));
        return p;
    }

}
