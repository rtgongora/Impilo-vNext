package zw.gov.mohcc.impilo.clinical.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.contraception.MedicalEligibilityEngine;
import zw.gov.mohcc.impilo.clinical.contraception.MedicalEligibilityService;
import zw.gov.mohcc.impilo.clinical.danger.DangerSignEvaluationService;
import zw.gov.mohcc.impilo.clinical.maternal.MaternalDangerSignService;
import zw.gov.mohcc.impilo.clinical.maternal.PostnatalNewbornService;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MedicalEligibilityControllerTest {

    private MedicalEligibilityController controller;

    @BeforeEach
    void setUp() {
        controller = new MedicalEligibilityController(new MedicalEligibilityService(new ObjectMapper()));
    }

    @Test
    @DisplayName("returns method eligibility for supplied facts")
    void assessReturnsMethods() {
        var response = controller.assess(new MedicalEligibilityController.AssessRequest(
                "INITIATION",
                Map.of("ageYears", 25, "smokingStatus", "NON_SMOKER"),
                null));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        var methods = (java.util.List<Map<String, Object>>) data.get("methods");
        assertThat(methods).isNotEmpty();
        assertThat(methods.get(0)).containsKeys("method_code", "recommendation");
    }

    @Test
    @DisplayName("severe hypertension restricts combined oral pill")
    void severeHypertensionRestrictsCoc() {
        var response = controller.assess(new MedicalEligibilityController.AssessRequest(
                "INITIATION",
                Map.of("ageYears", 30, "systolicBp", 180, "diastolicBp", 115),
                java.util.List.of("COMBINED_ORAL_PILL")));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        var methods = (java.util.List<Map<String, Object>>) data.get("methods");
        assertThat(methods.get(0).get("recommendation"))
                .isIn(MedicalEligibilityEngine.Recommendation.DO_NOT_USE.name(),
                        MedicalEligibilityEngine.Recommendation.SPECIALIST_JUDGEMENT_REQUIRED.name(),
                        MedicalEligibilityEngine.Recommendation.CANNOT_DETERMINE.name());
    }
}

class PostnatalCareControllerTest {

    private PostnatalCareController controller;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        RuleContentLoader loader = new RuleContentLoader(mapper);
        controller = new PostnatalCareController(
                new MaternalDangerSignService(loader),
                new PostnatalNewbornService(new DangerSignEvaluationService(loader), new MaternalDangerSignService(loader)));
    }

    private static Map<String, Object> facts(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("maternal: silence is not reassurance when screening incomplete")
    void maternalSilenceIsNotReassurance() {
        var response = controller.assessMaternal(
                new PostnatalCareController.MaternalAssessRequest(facts(), false));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("no_danger_signs_found")).isEqualTo(false);
        assertThat(data.get("screening_complete")).isEqualTo(false);
    }

    @Test
    @DisplayName("maternal: heavy bleeding fires when screening complete")
    void maternalHeavyBleeding() {
        var response = controller.assessMaternal(
                new PostnatalCareController.MaternalAssessRequest(
                        facts("vaginalBleeding", "HEAVY"), true));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        var alerts = (java.util.List<Map<String, Object>>) data.get("alerts");
        assertThat(alerts.stream().map(a -> a.get("code")).toList()).contains("PNC_SECONDARY_PPH");
    }

    @Test
    @DisplayName("newborn: PSBI delegates to young-infant definition")
    void newbornPsbiDelegation() {
        var response = controller.assessNewborn(
                new PostnatalCareController.NewbornAssessRequest(
                        10,
                        facts("feeding", "NOT_FEEDING", "vitals.temperature", 35.0),
                        true));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("psbi_delegation_note")).asString()
                .contains("young-infant");
        @SuppressWarnings("unchecked")
        Map<String, Object> psbi = (Map<String, Object>) data.get("psbi");
        @SuppressWarnings("unchecked")
        var alerts = (java.util.List<Map<String, Object>>) psbi.get("alerts");
        assertThat(alerts.stream().map(a -> a.get("code")).toList()).contains("PSBI_YOUNG_INFANT");
    }
}
