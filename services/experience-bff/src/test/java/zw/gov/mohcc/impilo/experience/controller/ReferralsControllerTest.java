package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReferralsControllerTest {

    @Test
    void acceptReferralReturnsCoreTransactionCorrelationMeta() {
        ReferralsController controller = new ReferralsController(new StubPctClient());
        UUID referralId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        var response = controller.acceptReferral(
                referralId,
                "tenant-1",
                "pod-1",
                "req-1",
                "corr-1",
                null,
                new ReferralsController.AcceptReferralRequest(
                        "facility-1",
                        "Harare Central",
                        "2026-06-08T09:00:00Z",
                        "Accepted for specialist review"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        assertEquals("req-1", meta.get("request_id"));
        assertEquals("referral-11111111-1111-1111-1111-111111111111", meta.get("core_transaction_id"));
    }

    private static final class StubPctClient extends PctServiceClient {
        private StubPctClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), new ObjectMapper());
        }

        @Override
        public JsonNode acceptReferral(String referralId, Map<String, Object> body) {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.createObjectNode()
                    .put("id", referralId)
                    .put("status", "ACCEPTED");
        }
    }
}
