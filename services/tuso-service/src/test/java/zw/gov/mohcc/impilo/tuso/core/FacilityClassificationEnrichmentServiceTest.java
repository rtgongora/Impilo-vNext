package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityClassificationMappingRuleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEnrichmentRunEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryProfileEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryProfileHistoryEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityClassificationMappingRuleRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityEnrichmentRunRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRegulatoryProfileHistoryRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRegulatoryProfileRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityClassificationEnrichmentServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilityRegulatoryProfileRepository profileRepository;
    @Mock private FacilityRegulatoryProfileHistoryRepository historyRepository;
    @Mock private FacilityEnrichmentRunRepository runRepository;
    @Mock private FacilityClassificationMappingRuleRepository ruleRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private FacilityClassificationEnrichmentService service;
    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final TrustContext ctx = new TrustContext(
            tenantId, "tester", "HPA_REGISTRAR", "REGULATION", null,
            UUID.randomUUID(), null, null, null, AccessMode.INTERNAL);

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var resource = new PathMatchingResourcePatternResolver()
                .getResources("classpath:regulatory/classification-mapping/*.json")[0];
        Map<String, Object> pack = mapper.readValue(resource.getInputStream(), new TypeReference<>() {});
        FacilityClassificationMappingRuleEntity rule = new FacilityClassificationMappingRuleEntity();
        rule.setCode("HPA_FACILITY_CLASSIFICATION_MAP");
        rule.setVersion(1);
        rule.setRuleJson(pack);
        lenient().when(ruleRepository.findFirstByStatusOrderByVersionDesc("ACTIVE"))
                .thenReturn(Optional.of(rule));
        ClassificationRuleEvaluator evaluator = new ClassificationRuleEvaluator(ruleRepository);

        // Runs get an id; profile/history saves echo the argument back.
        AtomicLong runSeq = new AtomicLong(1);
        lenient().when(runRepository.save(any())).thenAnswer(inv -> {
            FacilityEnrichmentRunEntity r = inv.getArgument(0);
            try {
                var idField = FacilityEnrichmentRunEntity.class.getDeclaredField("id");
                idField.setAccessible(true);
                if (idField.get(r) == null) idField.set(r, runSeq.getAndIncrement());
            } catch (ReflectiveOperationException ignored) { }
            return r;
        });
        lenient().when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new FacilityClassificationEnrichmentService(
                facilityRepository, profileRepository, historyRepository, runRepository, evaluator, outboxRepository);
    }

    private FacilityEntity facility(long id, String type, String ownership, String level, Object bedCapacity) {
        FacilityEntity f = new FacilityEntity();
        f.setId(id);
        f.setTenantId(tenantId);
        f.setFacilityType(type);
        f.setOwnership(ownership);
        f.setLevel(level);
        if (bedCapacity != null) {
            java.util.Map<String, Object> md = new java.util.LinkedHashMap<>();
            md.put("bed_capacity", bedCapacity);
            f.setMetadata(md);
        }
        return f;
    }

    private void estateIs(FacilityEntity... facilities) {
        when(facilityRepository.findByTenantId(eq(tenantId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(facilities)));
    }

    @Test
    void createsProfilesAndVerifiesTheEstateCountRatherThanAssumingIt() {
        estateIs(facility(1, "DISTRICT_HOSPITAL", "GOVERNMENT", "SECONDARY", 76),
                 facility(2, "CLINIC", "RURAL_DISTRICT_COUNCIL", "PRIMARY", null));
        when(profileRepository.findByFacilityId(anyLong())).thenReturn(Optional.empty());

        FacilityEnrichmentRunEntity run = service.runBackfill(ctx, false);

        assertThat(run.getRecordsTotal()).isEqualTo(2);        // verified, not assumed 1773
        assertThat(run.getRecordsCreated()).isEqualTo(2);
        assertThat(run.getEstateAudit()).containsKey("byFacilityType");
        verify(outboxRepository).save(any());                  // run-completed event emitted
    }

    @Test
    void dryRunEvaluatesButPersistsNothing() {
        estateIs(facility(1, "CLINIC", "GOVERNMENT", "PRIMARY", null));
        when(profileRepository.findByFacilityId(anyLong())).thenReturn(Optional.empty());

        FacilityEnrichmentRunEntity run = service.runBackfill(ctx, true);

        assertThat(run.getRecordsEvaluated()).isEqualTo(1);
        assertThat(run.getRecordsCreated()).isZero();
        verify(profileRepository, never()).save(any());
    }

    @Test
    void reRunIsANoOpWhenTheEvaluationFingerprintIsUnchanged() {
        FacilityEntity f = facility(1, "DISTRICT_HOSPITAL", "GOVERNMENT", "SECONDARY", 76);
        estateIs(f);
        // First pass to capture the fingerprint the engine would produce.
        FacilityRegulatoryProfileEntity existing = new FacilityRegulatoryProfileEntity();
        existing.setFacilityId(1L);
        existing.setClassificationStatus("DETERMINISTIC_MULTI_FIELD_MATCH");
        var evaluator = new ClassificationRuleEvaluator(ruleRepository);
        var outcome = evaluator.evaluate(new ClassificationRuleEvaluator.Inputs(
                "DISTRICT_HOSPITAL", "GOVERNMENT", 76, null, null));
        existing.setEvaluationFingerprint(outcome.evaluationFingerprint());
        when(profileRepository.findByFacilityId(1L)).thenReturn(Optional.of(existing));

        FacilityEnrichmentRunEntity run = service.runBackfill(ctx, false);

        assertThat(run.getRecordsUnchanged()).isEqualTo(1);
        assertThat(run.getRecordsUpdated()).isZero();
        verify(profileRepository, never()).save(any());
    }

    @Test
    void neverOverwritesAHumanConfirmedProfileAndFlagsDrift() {
        estateIs(facility(1, "DISTRICT_HOSPITAL", "GOVERNMENT", "SECONDARY", 76));
        FacilityRegulatoryProfileEntity confirmed = new FacilityRegulatoryProfileEntity();
        confirmed.setFacilityId(1L);
        confirmed.setClassificationStatus("HUMAN_CONFIRMED");
        confirmed.setHpaClass("A");                            // deliberately different from machine (C)
        confirmed.setHpaClassificationLabel("something a human chose");
        when(profileRepository.findByFacilityId(1L)).thenReturn(Optional.of(confirmed));

        FacilityEnrichmentRunEntity run = service.runBackfill(ctx, false);

        assertThat(run.getRecordsPreservedHuman()).isEqualTo(1);
        assertThat(run.getRecordsUpdated()).isZero();
        verify(profileRepository, never()).save(any());        // human row untouched
        assertThat(run.getExceptionReport())
                .anyMatch(e -> "HUMAN_CONFIRMED_MACHINE_DRIFT".equals(e.get("code")));
    }

    @Test
    void nonNumericBedCapacityIsTreatedAsAbsentWithAnException() {
        estateIs(facility(1, "DISTRICT_HOSPITAL", "GOVERNMENT", "SECONDARY", "N/a"));
        when(profileRepository.findByFacilityId(anyLong())).thenReturn(Optional.empty());

        FacilityEnrichmentRunEntity run = service.runBackfill(ctx, false);

        assertThat(run.getExceptionReport())
                .anyMatch(e -> "NON_NUMERIC_BED_CAPACITY".equals(e.get("code")));
        // No bed band could be derived → the hospital needs the bed fact, not a guessed one.
        assertThat(run.getCountsByStatus()).containsKey("MISSING_REQUIRED_FACTS");
    }
}
