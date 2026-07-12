package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityClassificationMappingRuleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityClassificationMappingRuleRepository;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ClassificationRuleEvaluatorTest {

    @Mock
    private FacilityClassificationMappingRuleRepository ruleRepository;

    private ClassificationRuleEvaluator evaluator;

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
        evaluator = new ClassificationRuleEvaluator(ruleRepository);
    }

    private ClassificationRuleEvaluator.Outcome eval(String type, String ownership,
                                                     Integer claimed, Integer confirmed, String careSetting) {
        return evaluator.evaluate(new ClassificationRuleEvaluator.Inputs(type, ownership, claimed, confirmed, careSetting));
    }

    @Test
    void publicClinicIsClassCButNeedsCareSettingForALabel() {
        var o = eval("CLINIC", "GOVERNMENT", null, null, null);
        assertThat(o.canonicalType()).isEqualTo("CLINIC");
        assertThat(o.hpaClass()).isEqualTo("C");                       // deterministic, definitional
        assertThat(o.classificationStatus()).isEqualTo("MISSING_REQUIRED_FACTS");
        assertThat(o.requiredFacts()).contains("care_setting");
        assertThat(o.hpaClassificationLabel()).isNull();               // no label without the fact
        assertThat(o.careSetting()).isEqualTo("UNKNOWN");              // never guessed
    }

    @Test
    void supplyingOutpatientCareSettingResolvesThePublicClinicLabel() {
        var o = eval("CLINIC", "RURAL_DISTRICT_COUNCIL", null, null, "OUTPATIENT_ONLY");
        assertThat(o.classificationStatus()).isEqualTo("DETERMINISTIC_MULTI_FIELD_MATCH");
        assertThat(o.hpaClass()).isEqualTo("C");
        assertThat(o.hpaClassificationLabel())
                .isEqualTo("Government, Local Authority and Mission: Clinics (out patients only)");
    }

    @Test
    void publicDistrictHospitalWithClaimedBedsResolvesDeterministically() {
        var o = eval("DISTRICT_HOSPITAL", "GOVERNMENT", 76, null, null);
        assertThat(o.canonicalType()).isEqualTo("HOSPITAL");
        assertThat(o.careSetting()).isEqualTo("INPATIENT");
        assertThat(o.hpaClass()).isEqualTo("C");
        assertThat(o.bedBand()).isEqualTo("BEDS_51_100");
        assertThat(o.classificationStatus()).isEqualTo("DETERMINISTIC_MULTI_FIELD_MATCH");
        assertThat(o.hpaClassificationLabel())
                .isEqualTo("Government, Local Authority and Mission: Hospital 51-100 beds");
    }

    @Test
    void publicHospitalUnderFiftyOneBedsIsACatalogueGapNeverRounded() {
        var o = eval("RURAL_HOSPITAL", "GOVERNMENT", 30, null, null);
        assertThat(o.classificationStatus()).isEqualTo("AMBIGUOUS");
        assertThat(o.hpaClassificationLabel()).isNull();
        assertThat(o.exceptions()).anyMatch(e -> "CATALOGUE_GAP_PUBLIC_HOSPITAL_UNDER_51_BEDS".equals(e.get("code")));
    }

    @Test
    void privateClinicIsAmbiguousBetweenAAndBUntilCareSettingKnown() {
        var o = eval("PRIVATE_CLINIC", "PRIVATE", null, null, null);
        assertThat(o.classificationStatus()).isEqualTo("AMBIGUOUS");
        assertThat(o.suggestion()).hasSize(2);
        assertThat(o.suggestion()).anyMatch(s -> "A".equals(s.get("classCode")))
                .anyMatch(s -> "B".equals(s.get("classCode")));
    }

    @Test
    void ownershipValueLeakedAsFacilityTypeIsUnmapped() {
        var o = eval("PRIVATE", "PRIVATE", null, null, null);
        assertThat(o.canonicalType()).isNull();
        assertThat(o.classificationStatus()).isEqualTo("UNMAPPED");
    }

    @Test
    void blankFacilityTypeNeedsTheTypeFact() {
        var o = eval("", "GOVERNMENT", null, null, null);
        assertThat(o.classificationStatus()).isEqualTo("MISSING_REQUIRED_FACTS");
        assertThat(o.requiredFacts()).contains("facility_type");
    }

    @Test
    void hospitalRequiringLevelReviewIsAlwaysAmbiguous() {
        var o = eval("HOSPITAL_REQUIRES_LEVEL_REVIEW", "GOVERNMENT", 120, null, null);
        assertThat(o.classificationStatus()).isEqualTo("AMBIGUOUS");
    }

    @Test
    void healthCentreSingletonIsASuggestionOnly() {
        var o = eval("HC", "GOVERNMENT", null, null, null);
        assertThat(o.canonicalType()).isEqualTo("RURAL_HEALTH_CENTRE");
        assertThat(o.classificationStatus()).isEqualTo("SUGGESTED_HIGH_CONFIDENCE");
    }

    @Test
    void publicHospitalWithoutBedDataNeedsTheBedFact() {
        var o = eval("MISSION_HOSPITAL", "MISSION_FAITH_BASED", null, null, null);
        assertThat(o.hpaClass()).isEqualTo("C");
        assertThat(o.classificationStatus()).isEqualTo("MISSING_REQUIRED_FACTS");
        assertThat(o.requiredFacts()).contains("bed_count_confirmed");
    }

    @Test
    void privateHospitalWithBedsIsClassADeterministic() {
        var o = eval("PRIVATE_HOSPITAL", "PRIVATE", 40, null, null);
        assertThat(o.hpaClass()).isEqualTo("A");
        assertThat(o.classificationStatus()).isEqualTo("DETERMINISTIC_MULTI_FIELD_MATCH");
        assertThat(o.hpaClassificationLabel()).isEqualTo("Hospital with up to 50 beds");
    }

    @Test
    void ownershipTypeConflictIsAmbiguousWithAnException() {
        // MISSION_CLINIC implies mission (public group) but ownership says PRIVATE.
        var o = eval("MISSION_CLINIC", "PRIVATE", null, null, "OUTPATIENT_ONLY");
        assertThat(o.classificationStatus()).isEqualTo("AMBIGUOUS");
        assertThat(o.exceptions()).anyMatch(e -> "OWNERSHIP_TYPE_CONFLICT".equals(e.get("code")));
    }
}
