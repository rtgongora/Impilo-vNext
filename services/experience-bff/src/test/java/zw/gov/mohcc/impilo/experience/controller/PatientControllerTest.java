package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PatientControllerTest {

    @Test
    void listPatients_returnsSeededDirectoryWhenVitoUnavailable() {
        PatientController controller = new PatientController(new UnavailableVitoClient());

        ResponseEntity<Map<String, Object>> response =
                controller.listPatients("req-1", "corr-1", 0, 20, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        List<?> data = (List<?>) response.getBody().get("data");
        assertFalse(data.isEmpty());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listPatients_filtersSeededDirectoryBySearchTerm() {
        PatientController controller = new PatientController(new UnavailableVitoClient());

        ResponseEntity<Map<String, Object>> response =
                controller.listPatients("req-2", "corr-2", 0, 20, "Moyo", null);

        assertEquals(200, response.getStatusCode().value());
        List<?> data = (List<?>) response.getBody().get("data");
        assertEquals(1, data.size());
        Map<?, ?> patient = (Map<?, ?>) data.get(0);
        Map<?, ?> attrs = (Map<?, ?>) patient.get("attributes");
        assertEquals("Tatenda", attrs.get("givenName"));
    }

    @Test
    void getPatient_returnsSeededPatientById() {
        PatientController controller = new PatientController(new UnavailableVitoClient());

        ResponseEntity<Map<String, Object>> response =
                controller.getPatient("pat-001", "req-3", "corr-3");

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("pat-001", data.get("id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class UnavailableVitoClient extends VitoServiceClient {
        UnavailableVitoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listClientRegistryClients(String search, String status, String verificationState, int page, int size) {
            throw new RuntimeException("vito unavailable");
        }

        @Override
        public JsonNode getClientRegistryProfile(String id) {
            throw new RuntimeException("vito unavailable");
        }

        @Override
        public JsonNode getPatient(String id) {
            throw new RuntimeException("vito unavailable");
        }
    }
}
