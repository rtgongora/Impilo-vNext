package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductRegistryControllerTest {

    @Test
    void searchForwardsRegistryPayloadWithRequestMetadata() {
        ProductRegistryController controller = new ProductRegistryController(new StubMsikaClient());
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("q", "glucometer");

        ResponseEntity<String> response = controller.search(params, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/json", response.getHeaders().getContentType().toString());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("corr-1", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("{\"data\":{\"items\":[{\"id\":\"ITEM-001\"}]}}", response.getBody());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return new ServiceClientConfig.ServiceEndpoints(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        );
    }

    private static final class StubMsikaClient extends MsikaServiceClient {
        private StubMsikaClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> search(MultiValueMap<String, String> queryParams) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"data\":{\"items\":[{\"id\":\"ITEM-001\"}]}}");
        }
    }
}
