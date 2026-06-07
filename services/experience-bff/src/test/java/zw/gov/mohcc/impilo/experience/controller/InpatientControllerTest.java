package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InpatientControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listAdmissions_returnsDataFromInpatientService() {
        InpatientController controller = new InpatientController(new StubInpatientClient());
        ResponseEntity<Map<String, Object>> response = controller.listAdmissions(
                "00000000-0000-4000-8000-000000000001",
                "req-1",
                "corr-1",
                "CPID-ZW-00001",
                null);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void createAdmission_returns201WithCoreTransactionMeta() {
        InpatientController controller = new InpatientController(new StubInpatientClient());
        ResponseEntity<Map<String, Object>> response = controller.createAdmission(
                "00000000-0000-4000-8000-000000000001",
                "req-1",
                "corr-1",
                Map.of("subjectCpid", "CPID-ZW-00001"));
        assertEquals(201, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        assertEquals("demo-admission", meta.get("admission_ref"));
        assertEquals("admission-demo-admission", meta.get("core_transaction_id"));
    }

    @Test
    void getActiveAdmissions_returnsCoreTransactionMeta() {
        InpatientController controller = new InpatientController(new StubInpatientClient());
        ResponseEntity<Map<String, Object>> response = controller.getActiveAdmissions(
                "CPID-ZW-00001",
                "f1000000-0000-0000-0000-000000000001",
                "req-2",
                "corr-2");
        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        assertEquals("demo-admission", meta.get("admission_ref"));
        assertEquals("admission-demo-admission", meta.get("core_transaction_id"));
    }

    @Test
    void transferAdmission_returnsCoreTransactionMeta() {
        InpatientController controller = new InpatientController(new StubInpatientClient());
        ResponseEntity<Map<String, Object>> response = controller.transferAdmission(
                "demo-admission",
                "req-3",
                "corr-3",
                Map.of("toWardId", "ward-2", "toBedId", "bed-9"));
        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        assertEquals("admission-demo-admission", meta.get("core_transaction_id"));
    }

    @Test
    void dischargeAdmission_returnsCoreTransactionMeta() {
        InpatientController controller = new InpatientController(new StubInpatientClient());
        ResponseEntity<Map<String, Object>> response = controller.dischargeAdmission(
                "demo-admission",
                "req-4",
                "corr-4",
                Map.of("disposition", "HOME"));
        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        assertEquals("admission-demo-admission", meta.get("core_transaction_id"));
    }

    private static final class StubInpatientClient extends InpatientServiceClient {
        StubInpatientClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), mapper);
        }

        @Override
        public JsonNode listAdmissions(String patientCpid) {
            ArrayNode rows = mapper.createArrayNode();
            rows.addObject()
                    .put("subjectCpid", patientCpid != null ? patientCpid : "CPID-ZW-00001")
                    .put("status", "ADMITTED");
            return rows;
        }

        @Override
        public JsonNode createAdmission(Map<String, Object> request) {
            return mapper.createObjectNode().put("id", "demo-admission");
        }

        @Override
        public JsonNode getActiveAdmissions(String subjectCpid, String facilityId) {
            return mapper.createObjectNode()
                    .put("id", "demo-admission")
                    .put("subjectCpid", subjectCpid)
                    .put("facilityId", facilityId)
                    .put("status", "ADMITTED");
        }

        @Override
        public JsonNode transferPatient(String admissionRef, Map<String, Object> body) {
            return mapper.createObjectNode()
                    .put("id", admissionRef)
                    .put("status", "TRANSFERRED");
        }

        @Override
        public JsonNode dischargeAdmission(String admissionRef, Map<String, Object> body) {
            return mapper.createObjectNode()
                    .put("id", admissionRef)
                    .put("status", "DISCHARGED");
        }
    }
}
