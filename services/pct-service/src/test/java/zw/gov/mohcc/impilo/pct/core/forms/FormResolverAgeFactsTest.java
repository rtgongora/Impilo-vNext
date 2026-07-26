package zw.gov.mohcc.impilo.pct.core.forms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import zw.gov.mohcc.impilo.pct.core.clinical.NewbornEpisodeService;
import zw.gov.mohcc.impilo.pct.integration.FormsCatalogIntegration;
import zw.gov.mohcc.impilo.pct.integration.VitoIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormResolverDecisionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.FormResolverDecisionRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Form resolution must know a child's age in days, not only in months: the sick-young-infant
 * assessment applies to 0-59 days and the neonatal bands to the first week and the first
 * month, and months cannot express those boundaries.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FormResolverAgeFactsTest {

    private static final String SUBJECT = "CPID-CHILD-1";

    @Mock private EncounterRepository encounterRepository;
    @Mock private VitoIntegration vitoIntegration;
    @Mock private FormsCatalogIntegration formsCatalogIntegration;
    @Mock private FormResolverDecisionRepository decisionRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private NewbornEpisodeService newbornEpisodeService;

    private FormResolverService resolverService;
    private TrustContext context;

    @BeforeEach
    void setUp() {
        resolverService = new FormResolverService(encounterRepository, vitoIntegration,
                formsCatalogIntegration, decisionRepository, outboxRepository, new ObjectMapper());
        ReflectionTestUtils.setField(resolverService, "newbornEpisodeService", newbornEpisodeService);
        context = new TrustContext(
                UUID.randomUUID(), "nurse-1", "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);
        when(formsCatalogIntegration.fetchCatalog()).thenReturn(List.of());
        when(decisionRepository.save(any(FormResolverDecisionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ageInDaysIsDerivedFromTheRecordedDateOfBirth() {
        LocalDate fortyDaysAgo = LocalDate.now().minusDays(40);
        when(vitoIntegration.resolvePatientByCpid(SUBJECT))
                .thenReturn(Map.of("dateOfBirth", fortyDaysAgo.toString(), "sex", "FEMALE"));

        PatientFacts facts = resolveFacts();

        assertThat(facts.ageDays()).isEqualTo(40);
        assertThat(facts.ageMonths()).isEqualTo(1);
        assertThat(facts.sex()).isEqualTo("FEMALE");
    }

    @Test
    void gestationalAgeAtBirthTravelsWithTheFactsForPretermChildren() {
        when(vitoIntegration.resolvePatientByCpid(SUBJECT))
                .thenReturn(Map.of("dateOfBirth", LocalDate.now().minusDays(60).toString(), "sex", "MALE"));
        when(newbornEpisodeService.gestationalAgeWeeks(SUBJECT)).thenReturn(32);

        assertThat(resolveFacts().gestationalAgeWeeks()).isEqualTo(32);
    }

    @Test
    void aChildWithNoBirthRecordSimplyHasNoGestationalAge() {
        // The normal case for anyone not born in the system; not an error.
        when(vitoIntegration.resolvePatientByCpid(SUBJECT))
                .thenReturn(Map.of("dateOfBirth", LocalDate.now().minusYears(6).toString(), "sex", "MALE"));
        when(newbornEpisodeService.gestationalAgeWeeks(SUBJECT)).thenReturn(null);

        assertThat(resolveFacts().gestationalAgeWeeks()).isNull();
    }

    @Test
    void formResolutionStillWorksWhenTheBirthRecordLookupFails() {
        when(vitoIntegration.resolvePatientByCpid(SUBJECT))
                .thenReturn(Map.of("dateOfBirth", LocalDate.now().minusDays(10).toString(), "sex", "FEMALE"));
        when(newbornEpisodeService.gestationalAgeWeeks(anyString()))
                .thenThrow(new IllegalStateException("birth record store unavailable"));

        PatientFacts facts = resolveFacts();

        assertThat(facts.ageDays()).isEqualTo(10);
        assertThat(facts.gestationalAgeWeeks()).isNull();
    }

    @Test
    void anUnknownDateOfBirthLeavesAgeUnknownRatherThanZero() {
        when(vitoIntegration.resolvePatientByCpid(SUBJECT)).thenReturn(Map.of("sex", "MALE"));

        PatientFacts facts = resolveFacts();

        assertThat(facts.ageDays()).isNull();
        assertThat(facts.ageMonths()).isNull();
    }

    @Test
    void theShorterConstructorStillWorksForCallersWithNoDayLevelAge() {
        PatientFacts facts = new PatientFacts(24, "MALE", false, List.of(), List.of());

        assertThat(facts.ageMonths()).isEqualTo(24);
        assertThat(facts.ageDays()).isNull();
        assertThat(facts.gestationalAgeWeeks()).isNull();
        assertThat(PatientFacts.unknown().ageDays()).isNull();
    }

    /**
     * The resolver audits the de-PII'd facts it used, so reading the audit row back proves
     * what the obligation engine actually saw.
     */
    private PatientFacts resolveFacts() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);
            resolverService.resolve(new FormResolveInput(
                    null, SUBJECT, "NURSE", "NURSE", null, "pediatric", 3,
                    "opd", null, "opd", null, null,
                    null, null, null, List.of(), List.of()));
        }
        ArgumentCaptor<FormResolverDecisionEntity> captor =
                ArgumentCaptor.forClass(FormResolverDecisionEntity.class);
        verify(decisionRepository).save(captor.capture());

        try {
            Map<?, ?> audited = new ObjectMapper()
                    .readValue(captor.getValue().getInPatientContext(), Map.class);
            return new PatientFacts(
                    intOrNull(audited.get("ageMonths")),
                    (String) audited.get("sex"),
                    (Boolean) audited.get("pregnant"),
                    List.of(),
                    List.of(),
                    intOrNull(audited.get("ageDays")),
                    intOrNull(audited.get("gestationalAgeWeeks")));
        } catch (Exception e) {
            throw new IllegalStateException("Audited patient context was not readable", e);
        }
    }

    private static Integer intOrNull(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
