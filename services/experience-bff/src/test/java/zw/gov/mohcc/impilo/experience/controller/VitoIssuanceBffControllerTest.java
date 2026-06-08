package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VitoIssuanceBffControllerTest {

    @Test
    void submitForwardsToVitoIssuancePath() {
        VitoIssuanceBffController controller = new VitoIssuanceBffController(new StubVitoClient());
        ResponseEntity<String> response = controller.submit(
                Map.of("healthId", "HID-1", "type", "FIRST_ISSUE"),
                "req-1",
                "corr-1");

        assertEquals(201, response.getStatusCode().value());
        assertTrue(response.getBody().contains("iss-req-1"));
        assertEquals("corr-1", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("/v1/internal/issuance/submit", StubVitoClient.lastPostPath);
    }

    @Test
    void queueForwardsPagingParams() {
        VitoIssuanceBffController controller = new VitoIssuanceBffController(new StubVitoClient());
        ResponseEntity<String> response = controller.queue("SUBMITTED", null, 1, 10, "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(StubVitoClient.lastGetPath.contains("state=SUBMITTED"));
        assertTrue(StubVitoClient.lastGetPath.contains("page=1"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubVitoClient extends VitoServiceClient {
        static String lastPostPath;
        static String lastGetPath;

        StubVitoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> rawPost(String path, Object body) {
            lastPostPath = path;
            return ResponseEntity.status(201)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"id\":\"iss-req-1\",\"state\":\"SUBMITTED\"}");
        }

        @Override
        public ResponseEntity<String> rawGet(String path) {
            lastGetPath = path;
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"content\":[{\"id\":\"iss-req-1\"}]}");
        }
    }
}
