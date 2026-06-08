package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.experience.facility.FacilityNameResolver;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VitoPrintBffControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void verifyPickupForwardsToVitoPrintPath() {
        VitoPrintBffController controller = new VitoPrintBffController(
                new StubVitoClient(),
                new FacilityNameResolver(new TusoServiceClient(new RestTemplate(), endpoints())),
                MAPPER);

        ResponseEntity<String> response = controller.verifyPickup(
                Map.of("pickupToken", "tok-abc"),
                "req-1",
                "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("verified"));
        assertEquals("/v1/print/card/verify-pickup", StubVitoClient.lastPostPath);
        assertEquals("corr-1", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
    }

    @Test
    void confirmHandoverForwardsBody() {
        VitoPrintBffController controller = new VitoPrintBffController(
                new StubVitoClient(),
                new FacilityNameResolver(new TusoServiceClient(new RestTemplate(), endpoints())),
                MAPPER);

        ResponseEntity<String> response = controller.confirmHandover(
                Map.of("jobId", "job-9", "handoverCode", "CODE-1"),
                "req-2",
                "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("/v1/print/card/confirm-handover", StubVitoClient.lastPostPath);
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubVitoClient extends VitoServiceClient {
        static String lastPostPath;

        StubVitoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> rawPost(String path, Object body) {
            lastPostPath = path;
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"verified\":true,\"facilityName\":\"FAC-001\"}");
        }
    }
}
