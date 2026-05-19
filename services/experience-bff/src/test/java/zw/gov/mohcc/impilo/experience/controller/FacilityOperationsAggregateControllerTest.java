package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.DataWarehouseServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FacilityOperationsAggregateControllerTest {

    @Test
    void nationalKpisFailsClosedWhenWarehousePayloadIsEmpty() {
        FacilityOperationsAggregateController controller = new FacilityOperationsAggregateController(
                new ThrowingPctClient(), new EmptyWarehouseClient());

        ResponseEntity<Map<String, Object>> response = controller.nationalKpis("req-1", "corr-1");

        assertEquals(502, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("DATA_WAREHOUSE_UNAVAILABLE", error.get("code"));
    }

    @Test
    void facilityQueueSnapshotsFailsClosedWhenPctUnavailableForAnyFacility() {
        FacilityOperationsAggregateController controller = new FacilityOperationsAggregateController(
                new ThrowingPctClient(), new EmptyWarehouseClient());
        String facilityId = UUID.randomUUID().toString();

        ResponseEntity<Map<String, Object>> response = controller.facilityQueueSnapshots(
                "req-1",
                "corr-1",
                Map.of("facility_ids", java.util.List.of(facilityId)));

        assertEquals(502, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("PCT_QUEUE_UNAVAILABLE", error.get("code"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class ThrowingPctClient extends PctServiceClient {
        private ThrowingPctClient() {
            super(new RestTemplate(), endpoints(), new ObjectMapper());
        }

        @Override
        public JsonNode listQueues(UUID facilityId, UUID workspaceId) {
            throw new IllegalStateException("pct unavailable");
        }
    }

    private static final class EmptyWarehouseClient extends DataWarehouseServiceClient {
        private EmptyWarehouseClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode goldStats() {
            return null;
        }
    }
}
