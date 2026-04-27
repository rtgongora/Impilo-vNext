package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommerceSubstitutionControllerTest {

    @Test
    void listSubstitutionsForwardsQueryPayload() {
        CommerceSubstitutionController controller = new CommerceSubstitutionController(new StubFlowClient());
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("status", "PENDING");

        ResponseEntity<String> response = controller.listSubstitutions(queryParams, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("{\"items\":[{\"orderId\":\"ORDER-1\",\"status\":\"PENDING\"}]}", response.getBody());
    }

    @Test
    void approveSubstitutionNormalizesEmptyBody() {
        CommerceSubstitutionController controller = new CommerceSubstitutionController(new StubFlowClient());

        ResponseEntity<String> response = controller.approveSubstitution("ORDER-1", "", "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"data\":{\"decision\":\"APPROVED\"}}", response.getBody());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubFlowClient extends MsikaFlowServiceClient {
        private StubFlowClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> listSubstitutions(MultiValueMap<String, String> queryParams) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"items\":[{\"orderId\":\"ORDER-1\",\"status\":\"" + queryParams.getFirst("status") + "\"}]}");
        }

        @Override
        public ResponseEntity<String> approveSubstitution(String orderId, String requestBody) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"data\":{\"decision\":\"APPROVED\"}}");
        }
    }
}
