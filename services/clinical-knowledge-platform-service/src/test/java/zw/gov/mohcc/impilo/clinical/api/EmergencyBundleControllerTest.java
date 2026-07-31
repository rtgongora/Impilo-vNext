package zw.gov.mohcc.impilo.clinical.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.clinical.maternal.EmergencyBundleService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmergencyBundleControllerTest {

    private final ObjectMapper json = new ObjectMapper();
    private final EmergencyBundleController controller = new EmergencyBundleController(
            new EmergencyBundleService(new ObjectMapper()));

    @Test
    @DisplayName("a freshly-called PPH shows mandatory steps outstanding, never zero")
    void freshlyCalledPphIsNotComplete() throws Exception {
        var response = controller.assessPph(request("""
                {"completedSteps": [], "minutesSinceTrigger": 0, "minutesSinceLastObservation": 0}
                """));

        Map<String, Object> data = data(response);
        assertThat(data.get("status")).isEqualTo("ACTIVE");
        assertThat((List<?>) data.get("outstanding_mandatory")).isNotEmpty();
        assertThat(data.get("may_close")).isEqualTo(false);
    }

    @Test
    @DisplayName("only explicit TRUE control with clinician confirmation may close")
    void closesOnlyWithEverything() throws Exception {
        var response = controller.assessPph(request("""
                {"completedSteps": [
                   "PPH_MASSAGE", "PPH_UTEROTONIC", "PPH_TXA", "PPH_IV_FLUIDS",
                   "PPH_EXAMINE_CAUSE", "PPH_ESCALATE"
                 ],
                 "minutesSinceTrigger": 25,
                 "minutesSinceLastObservation": 2,
                 "controlConfirmed": true,
                 "clinicianConfirmedClose": true}
                """));

        Map<String, Object> data = data(response);
        assertThat(data.get("status")).isEqualTo("CLOSABLE");
        assertThat(data.get("may_close")).isEqualTo(true);
    }

    @Test
    @DisplayName("eclampsia bundle assesses through the same engine with its own steps")
    void eclampsiaAssessReturnsChecklist() throws Exception {
        var response = controller.assessEclampsia(request("""
                {"completedSteps": [], "minutesSinceTrigger": 0}
                """));

        Map<String, Object> data = data(response);
        assertThat(data.get("status")).isEqualTo("ACTIVE");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) data.get("steps");
        assertThat(steps).anyMatch(s -> "ECL_MAGNESIUM_LOADING".equals(s.get("code")));
    }

    private EmergencyBundleController.AssessRequest request(String body) throws Exception {
        return json.readValue(body, EmergencyBundleController.AssessRequest.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }
}
