package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.NdilaServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminFacilityImportControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listRunsWrapsTusoDataInEnvelope() {
        var controller = controller(new StubTusoClient());

        ResponseEntity<Map<String, Object>> response = controller.listRuns("req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("data"));
        assertNotNull(response.getBody().get("meta"));
    }

    @Test
    void getRunReturnsNotFoundWhenTusoNull() {
        var controller = controller(new NullTusoClient());

        ResponseEntity<Map<String, Object>> response = controller.getRun(99L, "req-1", "corr-1");

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void reviewProxiesRealBuckets() {
        var controller = controller(new StubTusoClient());

        ResponseEntity<Map<String, Object>> response = controller.getRunReview(7L, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertTrue(data.has("importedRows"));
        assertTrue(data.has("missingFacilityCode"));
    }

    @Test
    void rowsProxiesRealPersistedRows() {
        var controller = controller(new StubTusoClient());

        ResponseEntity<Map<String, Object>> response =
                controller.getRunRows(7L, Map.of("outcome", "IMPORTED", "page", "0"), "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertTrue(data.has("content"));
        assertFalse(response.getBody().containsKey("error"));
    }

    @Test
    void duplicatesProxiesGroups() {
        var controller = controller(new StubTusoClient());

        ResponseEntity<Map<String, Object>> response =
                controller.getRunDuplicates(7L, "FACILITY_NAME", "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertTrue(data.has("groups"));
    }

    @Test
    void missingFieldChecklistSlicesProvenance() {
        var controller = controller(new StubTusoClient());

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

    @Test
    void supplyCodeProxiesToTuso() {
        var controller = controller(new StubTusoClient());

        ResponseEntity<Map<String, Object>> response = controller.supplyCode(
                7L, 1L, Map.of("facilityCode", "ZWX1"), "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("CORRECTED_READY_FOR_IMPORT", data.path("decisionStatus").asText());
    }

    @Test
    void applyApprovedProxiesToTuso() {
        var controller = controller(new StubTusoClient());

        ResponseEntity<Map<String, Object>> response =
                controller.applyApproved(7L, Map.of(), "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals(1, data.path("applied").asInt());
    }

    @Test
    void hpaGeocodeReviewProxiesNdilaQueueInEnvelope() {
        var controller = controller(new StubTusoClient());

        ResponseEntity<Map<String, Object>> response =
                controller.hpaGeocodeReview(200, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals(1, data.path("count").asInt());
        assertEquals("PENDING_REVIEW", data.path("queue").get(0).path("geocodeStatus").asText());
        assertNotNull(response.getBody().get("meta"));
        assertFalse(response.getBody().containsKey("error"));
    }

    @Test
    void downstreamConflictIsPropagatedNotMaskedAs502() {
        var controller = controller(new ConflictTusoClient());

        ResponseEntity<Map<String, Object>> response =
                controller.approve(7L, 1L, "req-1", "corr-1");

        assertEquals(409, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("error"));
    }

    /** Constructs the controller with stub Varapi/Ndila clients; only the TUSO stub varies per test. */
    private static AdminFacilityImportController controller(TusoServiceClient tusoClient) {
        return new AdminFacilityImportController(tusoClient, new StubVarapiClient(), new StubNdilaClient());
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
        public JsonNode getFacilityImportRunRows(long runId, String query) {
            return node("{\"content\":[{\"id\":1,\"facilityCode\":\"ZW1\",\"outcome\":\"IMPORTED\"}],"
                    + "\"page\":0,\"size\":50,\"totalElements\":1,\"totalPages\":1}");
        }

        @Override
        public JsonNode getFacilityImportRunReview(long runId) {
            return node("{\"runId\":7,\"importedRows\":{\"count\":1,\"preview\":[]},"
                    + "\"missingFacilityCode\":{\"count\":1,\"preview\":[]}}");
        }

        @Override
        public JsonNode getFacilityImportRunDuplicates(long runId, String type) {
            return node("{\"runId\":7,\"duplicateType\":\"FACILITY_NAME\",\"groupCount\":1,"
                    + "\"groups\":[{\"duplicateGroupKey\":\"NAME:twin\",\"size\":2,\"rows\":[]}]}");
        }

        @Override
        public JsonNode getFacilityImportProvenance(long facilityId) {
            return node("{\"identity\":{\"internalFacilityId\":55,\"facilityCode\":\"ZW010125\"},"
                    + "\"acceptableMissing\":{\"missingLatitude\":true},"
                    + "\"checklist\":[{\"key\":\"queues\",\"status\":\"PENDING_DOWNSTREAM\",\"owner\":\"PCT\"}],"
                    + "\"downstreamMaterialisationStatus\":\"PENDING_CONFIGURATION\"}");
        }

        @Override
        public JsonNode postImportRowAction(long runId, long rowId, String action, Object body) {
            return node("{\"id\":1,\"decisionStatus\":\"CORRECTED_READY_FOR_IMPORT\"}");
        }

        @Override
        public JsonNode postImportRunAction(long runId, String action, Object body) {
            return node("{\"applied\":1,\"skipped\":0,\"rows\":[]}");
        }
    }

    private static final class StubVarapiClient extends VarapiServiceClient {
        private StubVarapiClient() {
            super(new RestTemplate(), endpoints());
        }
    }

    private static final class StubNdilaClient extends NdilaServiceClient {
        private StubNdilaClient() {
            super(new RestTemplate(), endpoints(), MAPPER);
        }

        @Override
        public JsonNode hpaGeocodeReviewQueue(int limit) {
            return node("{\"count\":1,\"queue\":[{\"locationId\":\"loc-1\",\"facilityCode\":\"ZW010125\","
                    + "\"geocodeStatus\":\"PENDING_REVIEW\"}]}");
        }
    }

    private static final class ConflictTusoClient extends TusoServiceClient {
        private ConflictTusoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode postImportRowAction(long runId, long rowId, String action, Object body) {
            throw org.springframework.web.client.HttpClientErrorException.create(
                    org.springframework.http.HttpStatus.CONFLICT, "Conflict",
                    org.springframework.http.HttpHeaders.EMPTY, new byte[0], null);
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
