package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminFacilityImportControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listRunsWrapsTusoDataInEnvelope() {
        var controller = new AdminFacilityImportController(new StubTusoClient());

        ResponseEntity<Map<String, Object>> response = controller.listRuns("req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("data"));
        assertNotNull(response.getBody().get("meta"));
    }

    @Test
    void getRunReturnsNotFoundWhenTusoNull() {
        var controller = new AdminFacilityImportController(new NullTusoClient());

        ResponseEntity<Map<String, Object>> response = controller.getRun(99L, "req-1", "corr-1");

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void reviewReshapesRunTotalsIntoBuckets() {
        var controller = new AdminFacilityImportController(new StubTusoClient());

        ResponseEntity<Map<String, Object>> response = controller.getRunReview(7L, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("facility-import-run-review-v1", data.get("contract"));
        assertEquals(false, data.get("rowLevelDetailAvailable"));
        @SuppressWarnings("unchecked")
        Map<String, Object> buckets = (Map<String, Object>) data.get("buckets");
        assertEquals(1L, buckets.get("missing_facility_code"));
        assertEquals(2L, buckets.get("duplicate_facility_name"));
    }

    @Test
    void rowsRouteHonestlyReportsStagingNotPersisted() {
        var controller = new AdminFacilityImportController(new StubTusoClient());

        ResponseEntity<Map<String, Object>> response = controller.getRunRows(7L, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("NOT_PERSISTED", data.get("rowLevelStaging"));
    }

    @Test
    void missingFieldChecklistSlicesProvenance() {
        var controller = new AdminFacilityImportController(new StubTusoClient());

        ResponseEntity<Map<String, Object>> response =
                controller.missingFieldChecklist(55L, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("facility-missing-field-checklist-v1", data.get("contract"));
        assertNotNull(data.get("checklist"));
        assertEquals("PENDING_CONFIGURATION", data.get("downstreamMaterialisationStatus"));
        assertFalse(response.getBody().containsKey("error"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static JsonNode node(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final class StubTusoClient extends TusoServiceClient {
        private StubTusoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listFacilityImportRuns() {
            return node("{\"count\":1,\"runs\":[{\"runId\":7,\"status\":\"COMPLETED\"}]}");
        }

        @Override
        public JsonNode getFacilityImportRun(long runId) {
            return node("{\"runId\":7,\"totals\":{\"missingFacilityCode\":1,\"duplicateFacilityCode\":0,"
                    + "\"duplicateFacilityName\":2,\"acceptableMissing\":3}}");
        }

        @Override
        public JsonNode getFacilityImportProvenance(long facilityId) {
            return node("{\"identity\":{\"internalFacilityId\":55,\"facilityCode\":\"ZW010125\"},"
                    + "\"acceptableMissing\":{\"missingLatitude\":true},"
                    + "\"checklist\":[{\"key\":\"queues\",\"status\":\"PENDING_DOWNSTREAM\",\"owner\":\"PCT\"}],"
                    + "\"downstreamMaterialisationStatus\":\"PENDING_CONFIGURATION\"}");
        }
    }

    private static final class NullTusoClient extends TusoServiceClient {
        private NullTusoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getFacilityImportRun(long runId) {
            return null;
        }
    }
}
