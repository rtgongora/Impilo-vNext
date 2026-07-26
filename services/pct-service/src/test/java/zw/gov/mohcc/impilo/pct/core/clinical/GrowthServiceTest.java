package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.paediatrics.growth.GrowthEngine;
import zw.gov.mohcc.impilo.paediatrics.growth.GrowthStandard;
import zw.gov.mohcc.impilo.pct.integration.VitoIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.GrowthMeasurementEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.NewbornBirthRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.GrowthMeasurementRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.NewbornBirthRecordRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GrowthServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SUBJECT = "CPID-CHILD-1";

    @Mock private GrowthMeasurementRepository growthRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ClinicalAccessGuard accessGuard;
    @Mock private VitoIntegration vitoIntegration;
    @Mock private NewbornBirthRecordRepository newbornRepository;

    private GrowthService growthService;
    private TrustContext context;

    @BeforeEach
    void setUp() {
        growthService = new GrowthService(
                growthRepository, outboxRepository, new ObjectMapper(),
                accessGuard, vitoIntegration, new GrowthEngine(), newbornRepository);
        context = new TrustContext(
                TENANT, "nurse-1", "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);
        when(growthRepository.save(any(GrowthMeasurementEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void stampsWhoZScoresAndProvenanceAtWriteTime() {
        // Twelve-month-old boy weighing 7.0 kg: underweight against the WHO standard.
        demographics("2025-07-26", "MALE");

        GrowthMeasurementEntity row = record(body -> {
            body.put("weight_kg", "7.0");
            body.put("length_cm", "71.0");
            body.put("measured_at", "2026-07-26T09:00:00Z");
        });

        assertThat(row.getAgeDays()).isEqualTo(365);
        assertThat(row.getWeightForAgeZ()).isLessThan(BigDecimal.valueOf(-2));
        assertThat(row.getGrowthStandard()).isEqualTo(GrowthEngine.STANDARD_ID);
        assertThat(row.getGrowthEngineVersion()).isEqualTo(GrowthEngine.ENGINE_VERSION);
        assertThat(row.getScoringNote()).isNull();
    }

    @Test
    void scoresPretermInfantOnThePretermChartAtPostmenstrualAge() {
        demographics("2026-01-01", "FEMALE");

        GrowthMeasurementEntity row = record(body -> {
            body.put("weight_kg", "5.0");
            body.put("measured_at", "2026-05-01T09:00:00Z");
            body.put("gestational_age_weeks", 32);
        });

        assertThat(row.getAgeDays()).isEqualTo(120);
        assertThat(row.getCorrectedAgeDays()).isEqualTo(85);
        assertThat(row.isCorrectedAgeApplied()).isTrue();
        assertThat(row.getGestationalAgeWeeks()).isEqualTo(32);
        assertThat(row.getGestationalAgeSource()).isEqualTo("SUPPLIED");

        // 32 weeks at birth plus 17 completed weeks lived is 49 weeks postmenstrual age.
        assertThat(row.getPostmenstrualAgeWeeks()).isEqualTo(49);
        assertThat(row.getGrowthStandard()).isEqualTo(GrowthStandard.FENTON_2013_PRETERM_GROWTH.standardId());
        assertThat(row.getWeightForAgeZ()).isNotNull();
    }

    @Test
    void gestationalAgeIsReadFromTheBirthRecordWhenTheScreenDoesNotCarryIt() {
        // A growth screen has no reason to know a baby's gestation. Without this the
        // preterm chart could only ever be selected by the unit that recorded the birth,
        // and every later weighing would silently fall back to the term standard.
        demographics("2026-01-01", "MALE");
        NewbornBirthRecordEntity birth = new NewbornBirthRecordEntity();
        birth.setGestationalAgeWeeks(28);
        when(newbornRepository.findByTenantIdAndSubjectCpid(TENANT, SUBJECT)).thenReturn(Optional.of(birth));

        GrowthMeasurementEntity row = record(body -> {
            body.put("weight_kg", "1.05");
            body.put("measured_at", "2026-01-15T09:00:00Z");
        });

        assertThat(row.getGestationalAgeWeeks()).isEqualTo(28);
        assertThat(row.getGestationalAgeSource()).isEqualTo("NEWBORN_BIRTH_RECORD");
        assertThat(row.getPostmenstrualAgeWeeks()).isEqualTo(30);
        assertThat(row.getGrowthStandard()).isEqualTo(GrowthStandard.FENTON_2013_PRETERM_GROWTH.standardId());

        // The defect this closes: on the term standard this well infant scored below -6
        // and was classified as severely acutely malnourished.
        assertThat(row.getWeightForAgeZ()).isGreaterThan(BigDecimal.valueOf(-2));
        assertThat(row.getNutritionStatus()).isNotEqualTo("SEVERE_ACUTE_MALNUTRITION");
    }

    @Test
    void aChildWithNoRecordedGestationSaysSoRatherThanImplyingTerm() {
        demographics("2025-07-26", "MALE");
        when(newbornRepository.findByTenantIdAndSubjectCpid(TENANT, SUBJECT)).thenReturn(Optional.empty());

        GrowthMeasurementEntity row = record(body -> {
            body.put("weight_kg", "7.0");
            body.put("measured_at", "2026-07-26T09:00:00Z");
        });

        assertThat(row.getGestationalAgeWeeks()).isNull();
        assertThat(row.getGestationalAgeSource()).isEqualTo("NOT_RECORDED");
    }

    @Test
    void muacBelowTheSevereCutOffIsClassifiedAsSevereAcuteMalnutrition() {
        demographics("2025-01-26", "MALE");

        GrowthMeasurementEntity row = record(body -> {
            body.put("muac_cm", "10.9");
            body.put("weight_kg", "7.4");
            body.put("oedema", "ABSENT");
            body.put("measured_at", "2026-07-26T09:00:00Z");
        });

        assertThat(row.getNutritionStatus()).isEqualTo("SEVERE_ACUTE_MALNUTRITION");
        assertThat(row.getNutritionContentVersion()).isNotBlank();
    }

    @Test
    void oedemaIsSevereEvenWhenWeightLooksAdequate() {
        demographics("2025-01-26", "FEMALE");

        GrowthMeasurementEntity row = record(body -> {
            body.put("weight_kg", "11.0");
            body.put("muac_cm", "14.0");
            body.put("oedema", "PRESENT");
            body.put("measured_at", "2026-07-26T09:00:00Z");
        });

        assertThat(row.getNutritionStatus()).isEqualTo("SEVERE_ACUTE_MALNUTRITION");
    }

    @Test
    void unmeasuredNutritionIsNotAssessedRatherThanAdequate() {
        demographics("2025-01-26", "FEMALE");

        GrowthMeasurementEntity row = record(body -> {
            body.put("head_circumference_cm", "47.0");
            body.put("measured_at", "2026-07-26T09:00:00Z");
        });

        assertThat(row.getNutritionStatus()).isEqualTo("NOT_ASSESSED");
    }

    @Test
    void measurementIsStillStoredWhenTheChildCannotBeScored() {
        // Identity service unreachable: the measurement must survive, with the gap stated.
        when(vitoIntegration.resolvePatientByCpid(anyString()))
                .thenThrow(new IllegalStateException("VITO unavailable"));

        GrowthMeasurementEntity row = record(body -> {
            body.put("weight_kg", "9.0");
            body.put("measured_at", "2026-07-26T09:00:00Z");
        });

        assertThat(row.getWeightKg()).isEqualByComparingTo("9.0");
        assertThat(row.getWeightForAgeZ()).isNull();
        assertThat(row.getGrowthStandard()).isNull();
        assertThat(row.getScoringNote()).contains("Date of birth");
    }

    @Test
    void childOverFiveIsStoredWithTheReasonNoScoreWasProduced() {
        demographics("2018-01-01", "FEMALE");

        GrowthMeasurementEntity row = record(body -> {
            body.put("weight_kg", "22.0");
            body.put("height_cm", "118.0");
            body.put("measured_at", "2026-07-26T09:00:00Z");
        });

        assertThat(row.getWeightForAgeZ()).isNull();
        assertThat(row.getScoringNote()).contains("5-19");
    }

    @Test
    void writeIsBlockedWithoutACareRelationship() {
        demographics("2025-01-26", "MALE");
        org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "no care relationship"))
                .when(accessGuard).requireCareRelationship(any(), anyString(), any(), any());

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);
            Map<String, Object> body = baseBody();
            body.put("weight_kg", "9.0");

            assertThatThrownBy(() -> growthService.record(body))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
        verify(growthRepository, never()).save(any());
    }

    @Test
    void aRecordWithNoMeasurementAtAllIsRejected() {
        demographics("2025-01-26", "MALE");

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);
            Map<String, Object> body = baseBody();

            assertThatThrownBy(() -> growthService.record(body))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("At least one measurement");
        }
    }

    @Test
    void missingSubjectIsRejectedBeforeAnythingElseHappens() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);

            assertThatThrownBy(() -> growthService.record(new LinkedHashMap<>()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("patient_id");
        }
        verify(accessGuard, never()).requireCareRelationship(any(), anyString(), any(), any());
    }

    @Test
    void everyRecordedMeasurementPublishesAnOutboxEvent() {
        demographics("2025-07-26", "MALE");

        record(body -> {
            body.put("weight_kg", "9.6");
            body.put("measured_at", "2026-07-26T09:00:00Z");
        });

        org.mockito.ArgumentCaptor<EventOutboxEntity> captor =
                org.mockito.ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("pct.growth.recorded");
        assertThat(captor.getValue().getAggregateType()).isEqualTo("GROWTH_MEASUREMENT");
        assertThat(captor.getValue().getPayload()).contains("subjectCpid");
    }

    @Test
    void trajectoryReadsStoredMeasurementsOldestFirstAndDetectsFaltering() {
        when(growthRepository.findByTenantIdAndSubjectCpidOrderByMeasuredAtAsc(TENANT, SUBJECT))
                .thenReturn(List.of(
                        stored(180, "7.2", "-1.4"),
                        stored(300, "7.9", "-2.2")));

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);

            var assessment = growthService.trajectory(SUBJECT);

            assertThat(assessment.faltering()).isTrue();
            assertThat(assessment.zScoreChange()).isEqualByComparingTo("-0.80");
        }
    }

    // --- helpers ---------------------------------------------------------------------

    private void demographics(String dateOfBirth, String sex) {
        when(vitoIntegration.resolvePatientByCpid(anyString()))
                .thenReturn(Map.of("dateOfBirth", dateOfBirth, "sex", sex));
    }

    private GrowthMeasurementEntity record(Consumer<Map<String, Object>> customiser) {
        Map<String, Object> body = baseBody();
        customiser.accept(body);
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);
            return growthService.record(body);
        }
    }

    private static Map<String, Object> baseBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patient_id", SUBJECT);
        body.put("encounter_id", "ENC-1");
        return body;
    }

    private static GrowthMeasurementEntity stored(int ageDays, String weightKg, String waz) {
        GrowthMeasurementEntity row = new GrowthMeasurementEntity();
        row.setAgeDays(ageDays);
        row.setWeightKg(new BigDecimal(weightKg));
        row.setWeightForAgeZ(new BigDecimal(waz));
        return row;
    }
}
