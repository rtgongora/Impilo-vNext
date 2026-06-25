package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the citizen clinical-record reads (G054) resolve the patient from {@code X-Actor-ID}
 * and delegate to the existing sovereign-service clients, wrapping the result in the data/meta envelope.
 */
@ExtendWith(MockitoExtension.class)
class CitizenClinicalRecordsControllerTest {

    @Mock private PctServiceClient pctClient;
    @Mock private InpatientServiceClient inpatientClient;

    private final ObjectMapper mapper = new ObjectMapper();

    private CitizenClinicalRecordsController controller() {
        return new CitizenClinicalRecordsController(pctClient, inpatientClient);
    }

    @Test
    void immunizationsDelegateToPctForTheActor() {
        ArrayNode body = mapper.createArrayNode();
        body.add(mapper.createObjectNode().put("vaccineName", "BCG"));
        when(pctClient.listImmunizations("CPID-1", 0, 20)).thenReturn(body);

        ResponseEntity<Map<String, Object>> res =
                controller().immunizations("req", "corr", "CPID-1", 0, 20);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody().get("data")).isEqualTo(body);
        verify(pctClient).listImmunizations(eq("CPID-1"), eq(0), eq(20));
    }

    @Test
    void carePlansDelegateToInpatientForTheActor() {
        ArrayNode body = mapper.createArrayNode();
        when(inpatientClient.listCarePlans("CPID-2")).thenReturn(body);

        ResponseEntity<Map<String, Object>> res = controller().carePlans("req", "corr", "CPID-2");

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody().get("data")).isEqualTo(body);
        verify(inpatientClient).listCarePlans("CPID-2");
    }

    @Test
    void referralsDelegateToPctForTheActor() {
        ArrayNode body = mapper.createArrayNode();
        when(pctClient.listPatientReferrals("CPID-3", 0, 20)).thenReturn(body);

        ResponseEntity<Map<String, Object>> res = controller().referrals("req", "corr", "CPID-3", 0, 20);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        verify(pctClient).listPatientReferrals("CPID-3", 0, 20);
        assertThat(res.getBody()).containsKey("meta");
    }

    @Test
    void sizeIsCappedAt100() {
        when(pctClient.listImmunizations("CPID-1", 0, 100)).thenReturn(mapper.createArrayNode());
        controller().immunizations("req", "corr", "CPID-1", 0, 500);
        verify(pctClient).listImmunizations("CPID-1", 0, 100);
    }
}
