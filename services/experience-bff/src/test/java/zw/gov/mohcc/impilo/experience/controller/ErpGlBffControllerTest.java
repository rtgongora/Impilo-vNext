package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.GeneralLedgerServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ErpGlBffControllerTest {

    @Test
    void accountsFailClosesWithTypedErrorWhenGlUnavailable() {
        ErpGlBffController controller = new ErpGlBffController(new FailingGlClient());

        ResponseEntity<Map<String, Object>> response = controller.accounts("req-gl-1", "corr-gl-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("GL_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        assertEquals("corr-gl-1", ((Map<?, ?>) response.getBody().get("meta")).get("correlation_id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class FailingGlClient extends GeneralLedgerServiceClient {
        private FailingGlClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getAccounts() {
            throw new RuntimeException("gl unavailable");
        }
    }
}
