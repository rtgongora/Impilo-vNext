package zw.gov.mohcc.impilo.pct.core.forms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import zw.gov.mohcc.impilo.pct.integration.FormsCatalogIntegration;
import zw.gov.mohcc.impilo.pct.integration.VitoIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.ObservationEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.FormResolverDecisionRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ObservationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * A patient whose clinic measured a creatinine now has a renal function fact.
 *
 * <p>{@code PatientFacts.renalFunction} was read only from a stored eGFR observation, and
 * Zimbabwean laboratories report a creatinine and leave the filtration rate to whoever reads the
 * result. So the fact was absent for the ordinary patient, and every rule keyed on {@code egfr}
 * reported it unassessed. That is right on missing data and no use at the bedside.</p>
 *
 * <p>The two properties that must not break are asserted here as well as the derivation itself: a
 * reported eGFR is never displaced, and a creatinine that cannot be read leaves the fact absent
 * rather than producing a number.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FormResolverRenalFunctionTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SUBJECT = "CPID-ADULT-1";
    private static final String CREATININE = "2160-0";
    private static final String REPORTED_EGFR = "62238-1";

    @Mock private EncounterRepository encounterRepository;
    @Mock private VitoIntegration vitoIntegration;
    @Mock private FormsCatalogIntegration formsCatalogIntegration;
    @Mock private FormResolverDecisionRepository decisionRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ObservationRepository observationRepository;

    private FormResolverService service;

    @BeforeEach
    void setUp() {
        service = new FormResolverService(encounterRepository, vitoIntegration,
                formsCatalogIntegration, decisionRepository, outboxRepository, new ObjectMapper());
        ReflectionTestUtils.setField(service, "observationRepository", observationRepository);
        when(observationRepository
                .findByTenantIdAndSubjectCpidAndCodeOrderByEffectiveAtDesc(eq(TENANT), anyString(), anyString()))
                .thenReturn(List.of());
    }

    private void record(String code, String value, String unit, LocalDate on) {
        ObservationEntity o = new ObservationEntity();
        o.setTenantId(TENANT);
        o.setSubjectCpid(SUBJECT);
        o.setCode(code);
        o.setValueQuantity(value == null ? null : new BigDecimal(value));
        o.setValueUnit(unit);
        o.setEffectiveAt(on == null ? null : on.atStartOfDay().atOffset(ZoneOffset.UTC));
        when(observationRepository.findByTenantIdAndSubjectCpidAndCodeOrderByEffectiveAtDesc(
                TENANT, SUBJECT, code)).thenReturn(List.of(o));
    }

    /** A 68-year-old woman, 420 µmol/L — severe renal impairment. */
    private DatedMeasurement resolveForOlderWoman() {
        return service.renalFunction(TENANT, SUBJECT, 68 * 12, "FEMALE");
    }

    @Test
    void aStoredCreatinineNowProducesARenalFunctionFact() {
        record(CREATININE, "420", "umol/L", LocalDate.now().minusDays(10));

        DatedMeasurement renal = resolveForOlderWoman();

        assertThat(renal).isNotNull();
        assertThat(renal.value()).isLessThan(30.0);
        assertThat(renal.unit()).isEqualTo("mL/min/1.73m2");
    }

    @Test
    void theDerivedRateSaysItWasDerivedRatherThanReported() {
        record(CREATININE, "420", "umol/L", LocalDate.now().minusDays(10));

        assertThat(resolveForOlderWoman().source())
                .isEqualTo(FormResolverService.DERIVED_EGFR_SOURCE);
    }

    @Test
    void theRateCarriesTheCreatininesDateSoItAgesOutWithIt() {
        // A rate derived from a six-month-old creatinine is six months old, however recently it was
        // computed. Stamping it today would make a stale value look current, and unlike a missing
        // one a stale value looks entirely usable.
        LocalDate drawn = LocalDate.now().minusMonths(6);
        record(CREATININE, "420", "umol/L", drawn);

        assertThat(resolveForOlderWoman().measuredOn()).isEqualTo(drawn);
    }

    @Test
    void aReportedEgfrIsUsedAndTheCreatinineIsNotConsulted() {
        record(REPORTED_EGFR, "77", "mL/min/1.73m2", LocalDate.now().minusDays(2));
        record(CREATININE, "420", "umol/L", LocalDate.now().minusDays(10));

        DatedMeasurement renal = resolveForOlderWoman();

        assertThat(renal.value()).isEqualTo(77.0);
        assertThat(renal.source()).isNotEqualTo(FormResolverService.DERIVED_EGFR_SOURCE);
    }

    @Test
    void aMilligramPerDecilitreCreatinineIsConverted() {
        record(CREATININE, "4.75", "mg/dL", LocalDate.now());

        // 4.75 mg/dL is 420 µmol/L, so this must land where the µmol/L case did.
        assertThat(resolveForOlderWoman().value()).isLessThan(30.0);
    }

    @Test
    void anUnrecognisedUnitLeavesTheFactAbsentRatherThanGuessing() {
        record(CREATININE, "47.5", "mg/L", LocalDate.now());

        assertThat(resolveForOlderWoman()).isNull();
    }

    @Test
    void anUnknownSexLeavesTheFactAbsentBecauseTheEquationHasNoNeutralCoefficient() {
        record(CREATININE, "420", "umol/L", LocalDate.now());

        assertThat(service.renalFunction(TENANT, SUBJECT, 68 * 12, "UNKNOWN")).isNull();
    }

    @Test
    void anUnknownAgeLeavesTheFactAbsent() {
        record(CREATININE, "420", "umol/L", LocalDate.now());

        assertThat(service.renalFunction(TENANT, SUBJECT, null, "FEMALE")).isNull();
    }

    @Test
    void aChildLeavesTheFactAbsentBecauseCkdEpiIsAnAdultEquation() {
        record(CREATININE, "60", "umol/L", LocalDate.now());

        assertThat(service.renalFunction(TENANT, SUBJECT, 8 * 12, "FEMALE")).isNull();
    }

    @Test
    void noObservationsAtAllLeavesTheFactAbsent() {
        assertThat(resolveForOlderWoman()).isNull();
    }

    @Test
    void anImplausibleCreatinineLeavesTheFactAbsentRatherThanScoringOnIt() {
        // 1.1 is a mg/dL value entered as µmol/L; deriving from it reports a rate in the thousands.
        record(CREATININE, "1.1", "umol/L", LocalDate.now());

        assertThat(resolveForOlderWoman()).isNull();
    }

    @Test
    void aDerivedRateReachesTheRuleFactsWithItsEquationNamed() {
        record(CREATININE, "420", "umol/L", LocalDate.now().minusDays(10));
        PatientFacts facts = PatientFacts.unknown()
                .withClinicalFacts(null, resolveForOlderWoman(), null, null);

        var ruleFacts = facts.toRuleFacts(LocalDate.now());

        assertThat(ruleFacts).containsKey("egfr");
        assertThat(ruleFacts).containsEntry("egfrEquation", FormResolverService.DERIVED_EGFR_SOURCE);
        assertThat(ruleFacts).containsEntry("egfrMeasuredDaysAgo", 10L);
    }
}
