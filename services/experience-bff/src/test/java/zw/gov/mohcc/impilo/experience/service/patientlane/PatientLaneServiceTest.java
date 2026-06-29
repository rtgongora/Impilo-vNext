package zw.gov.mohcc.impilo.experience.service.patientlane;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientLaneServiceTest {

    private static final ObjectMapper M = new ObjectMapper();

    @Mock private PctServiceClient pctClient;
    @Mock private InpatientServiceClient inpatientClient;

    private PatientLaneService service() {
        return new PatientLaneService(pctClient, inpatientClient);
    }

    private com.fasterxml.jackson.databind.JsonNode json(String s) throws Exception {
        return M.readTree(s);
    }

    @Test
    void visitStatus_maps_journey_state_to_plain_language() throws Exception {
        when(pctClient.getJourney("TX-1")).thenReturn(json(
                "{\"journeyId\":\"TX-1\",\"state\":\"QUEUED\",\"facilityId\":\"fac-1\",\"updatedAt\":\"2026-06-29T10:00:00Z\"}"));

        Map<String, Object> out = service().visitStatus("TX-1");

        assertThat(out.get("available")).isEqualTo(true);
        assertThat(out.get("state")).isEqualTo("QUEUED");
        assertThat(out.get("stage")).isEqualTo("PREPARE_FOR_CARE");
        assertThat(out.get("statusLabel")).isEqualTo("In the queue");
        assertThat((String) out.get("message")).contains("queue");
        assertThat(out.get("messageKey")).isEqualTo("patient.visit.queued");
        assertThat(out.get("facilityId")).isEqualTo("fac-1");
    }

    @Test
    void visitStatus_unknown_journey_is_available_false_not_an_error() {
        when(pctClient.getJourney("TX-MISSING")).thenReturn(null);

        Map<String, Object> out = service().visitStatus("TX-MISSING");

        assertThat(out.get("available")).isEqualTo(false);
        assertThat(out.get("messageKey")).isEqualTo("patient.visit.unknown");
    }

    @Test
    void visitStatus_downstream_failure_degrades_honestly() {
        when(pctClient.getJourney("TX-DOWN")).thenThrow(new RuntimeException("connect refused"));

        Map<String, Object> out = service().visitStatus("TX-DOWN");

        assertThat(out.get("available")).isEqualTo(false);
        assertThat(out.get("kind")).isEqualTo("visit");
    }

    @Test
    void inpatientStatus_active_admission_maps_to_on_the_ward_with_locators() throws Exception {
        when(inpatientClient.getAdmission("ADM-1")).thenReturn(json(
                "{\"status\":\"ADMITTED\",\"wardName\":\"Ward 4\",\"bedLabel\":\"B-12\"}"));

        Map<String, Object> out = service().inpatientStatus("ADM-1");

        assertThat(out.get("available")).isEqualTo(true);
        assertThat(out.get("stage")).isEqualTo("RECEIVE_CARE");
        assertThat(out.get("statusLabel")).isEqualTo("On the ward");
        assertThat(out.get("ward")).isEqualTo("Ward 4");
        assertThat(out.get("bed")).isEqualTo("B-12");
    }

    @Test
    void inpatientStatus_discharged_is_not_active() throws Exception {
        when(inpatientClient.getAdmission("ADM-2")).thenReturn(json("{\"status\":\"DISCHARGED\"}"));

        Map<String, Object> out = service().inpatientStatus("ADM-2");

        assertThat(out.get("available")).isEqualTo(true);
        assertThat(out.get("statusLabel")).isEqualTo("Discharged");
        assertThat(out.get("messageKey")).isEqualTo("patient.inpatient.discharged");
    }

    /**
     * Persona journey (SYS-3): one patient's visit advances through the canonical PCT lifecycle and
     * the patient lane reflects the right person-journey stage + plain language at every step.
     */
    @Test
    void persona_journey_reflects_every_stage_in_plain_language() throws Exception {
        record Step(String state, String stage, String label) {}
        var journey = java.util.List.of(
                new Step("ARRIVED", "CHECK_IN", "Checked in"),
                new Step("TRIAGED", "PREPARE_FOR_CARE", "Triage done"),
                new Step("QUEUED", "PREPARE_FOR_CARE", "In the queue"),
                new Step("IN_SERVICE", "RECEIVE_CARE", "Being seen"),
                new Step("ADMITTED", "RECEIVE_CARE", "Admitted"),
                new Step("DISCHARGED", "CONTINUE_CARE", "Discharged"));

        PatientLaneService svc = service();
        for (Step step : journey) {
            when(pctClient.getJourney("TX-J")).thenReturn(json(
                    "{\"journeyId\":\"TX-J\",\"state\":\"" + step.state() + "\"}"));
            Map<String, Object> out = svc.visitStatus("TX-J");
            assertThat(out.get("available")).as(step.state()).isEqualTo(true);
            assertThat(out.get("state")).isEqualTo(step.state());
            assertThat(out.get("stage")).as("stage for " + step.state()).isEqualTo(step.stage());
            assertThat(out.get("statusLabel")).as("label for " + step.state()).isEqualTo(step.label());
            assertThat((String) out.get("message")).as("message non-empty for " + step.state()).isNotBlank();
        }
    }
}
