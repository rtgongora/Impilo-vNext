package zw.gov.mohcc.impilo.experience.wellness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.SimbaServiceClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WellnessHomeControllerTest {

    @Mock private SimbaServiceClient simba;
    @Mock private PctServiceClient pct;

    private final ObjectMapper mapper = new ObjectMapper();

    private WellnessHomeController controller() {
        return new WellnessHomeController(simba, pct);
    }

    private ObjectNode node(String k, String v) {
        ObjectNode n = mapper.createObjectNode();
        n.put(k, v);
        return n;
    }

    @Test
    @SuppressWarnings("unchecked")
    void overview_aggregates_simba_sections_and_degrades_gracefully() {
        when(simba.getWellnessProfile("CPID-1")).thenReturn(node("display_name", "Tariro"));
        when(simba.listGoals("CPID-1")).thenReturn(mapper.createArrayNode());
        when(simba.listPlans()).thenReturn(mapper.createArrayNode());
        when(simba.listEnrollments("CPID-1")).thenReturn(mapper.createArrayNode());
        when(simba.listHabitCheckIns("CPID-1")).thenReturn(mapper.createArrayNode());
        when(simba.listCoaching("CPID-1")).thenReturn(mapper.createArrayNode());
        // care-linkages downstream is down → section should be null, not a 500
        when(simba.listCareLinkages("CPID-1")).thenThrow(new RuntimeException("simba down"));
        when(simba.listClubs()).thenReturn(mapper.createArrayNode());

        ResponseEntity<Map<String, Object>> resp =
                controller().overview(null, "CPID-1", "req", "corr");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        assertThat(data.get("person_cpid")).isEqualTo("CPID-1");
        assertThat(data.get("profile")).isNotNull();
        assertThat(data).containsKey("care_linkages"); // present even though it failed (null)
        assertThat(data.get("care_linkages")).isNull();
    }

    @Test
    void overview_requires_a_person_anchor() {
        ResponseEntity<Map<String, Object>> resp =
                controller().overview(null, null, "req", "corr");
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(simba);
    }

    @Test
    @SuppressWarnings("unchecked")
    void care_routing_records_linkage_and_creates_pct_referral_for_clinical_trigger() {
        when(simba.routeCare(any())).thenReturn(node("status", "OPEN"));
        when(pct.createReferral(any())).thenReturn(node("id", "REF-1"));

        ResponseEntity<Map<String, Object>> resp = controller().routeCare(
                null, "CPID-1", "req", "corr",
                Map.of("trigger", "RISK_THRESHOLD", "severity", "ROUTINE", "reason", "elevated BP reading"));

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        assertThat(data.get("referral_status")).isEqualTo("ROUTED");
        verify(simba).routeCare(any());
        verify(pct).createReferral(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void care_routing_emergency_skips_routine_referral_and_returns_emergency_guidance() {
        when(simba.routeCare(any())).thenReturn(node("status", "OPEN"));

        ResponseEntity<Map<String, Object>> resp = controller().routeCare(
                null, "CPID-1", "req", "corr",
                Map.of("trigger", "RED_FLAG", "severity", "EMERGENCY", "reason", "chest pain"));

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        assertThat(data.get("referral_status")).isEqualTo("EMERGENCY_ROUTED");
        assertThat(data).containsKey("emergency_guidance");
        verify(simba).routeCare(any());
        verifyNoInteractions(pct);
    }

    @Test
    @SuppressWarnings("unchecked")
    void care_routing_is_partial_success_when_pct_referral_fails() {
        when(simba.routeCare(any())).thenReturn(node("status", "OPEN"));
        when(pct.createReferral(any())).thenThrow(new RuntimeException("pct down"));

        ResponseEntity<Map<String, Object>> resp = controller().routeCare(
                null, "CPID-1", "req", "corr",
                Map.of("trigger", "SCREENING_DUE", "severity", "ROUTINE"));

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        assertThat(data.get("referral_status")).isEqualTo("REFERRAL_PENDING");
        assertThat(data).containsKey("linkage");
    }
}
