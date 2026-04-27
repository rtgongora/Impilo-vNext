package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ButanoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SummaryProxyControllerTest {

    @Test
    void forwardsFhirBundleResponsesWithoutWrapping() {
        SummaryProxyController controller = new SummaryProxyController(new StubButanoClient());

        ResponseEntity<String> response = controller.getIpsSummary("ZW-CPID-001", "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/fhir+json", response.getHeaders().getContentType().toString());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("corr-1", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("{\"resourceType\":\"Bundle\"}", response.getBody());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubButanoClient extends ButanoServiceClient {
        private StubButanoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> getIpsSummary(String cpid) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/fhir+json"))
                    .body("{\"resourceType\":\"Bundle\"}");
        }
    }
}
