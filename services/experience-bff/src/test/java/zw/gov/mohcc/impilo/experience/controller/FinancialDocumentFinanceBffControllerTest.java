package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FinancialDocumentFinanceBffControllerTest {

    @Test
    void generateFailClosesWithTypedErrorWhenCostaUnavailable() {
        FinancialDocumentFinanceBffController controller =
                new FinancialDocumentFinanceBffController(new FailingCostaClient());

        ResponseEntity<Map<String, Object>> response = controller.generate(Map.of(), "req-fdoc-1", "corr-fdoc-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COSTA_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class FailingCostaClient extends CostaServiceClient {
        private FailingCostaClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode postFinanceDocumentGenerate(Map<String, Object> body) {
            throw new RuntimeException("costa unavailable");
        }
    }
}
