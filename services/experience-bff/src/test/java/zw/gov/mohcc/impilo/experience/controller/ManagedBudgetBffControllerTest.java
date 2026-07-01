package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManagedBudgetBffControllerTest {

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    @Test
    void create_passesThroughAndPreservesHeaders() {
        ManagedBudgetBffController c = new ManagedBudgetBffController(new StubCostaClient());
        ResponseEntity<String> r = c.create(Map.of("title", "HIV 2026"), "req-9", "corr-9");
        assertEquals(201, r.getStatusCode().value());
        assertEquals("req-9", r.getHeaders().getFirst("X-Request-ID"));
        assertEquals("corr-9", r.getHeaders().getFirst("X-Correlation-ID"));
    }

    @Test
    void variance_returnsUpstreamStatusOnFailure_safePartial() {
        ManagedBudgetBffController c = new ManagedBudgetBffController(new FailingCostaClient());
        ResponseEntity<String> r = c.variance(java.util.UUID.randomUUID(), null, "req-1", "corr-1");
        assertEquals(503, r.getStatusCode().value());
        assertEquals("corr-1", r.getHeaders().getFirst("X-Correlation-ID"));
    }

    private static final class StubCostaClient extends CostaServiceClient {
        StubCostaClient() { super(new RestTemplate(), endpoints()); }
        @Override public ResponseEntity<String> costaInternalPost(String path, Object body) {
            return ResponseEntity.status(HttpStatus.CREATED).body("{\"success\":true,\"data\":{\"budgetId\":\"b1\"}}");
        }
    }

    private static final class FailingCostaClient extends CostaServiceClient {
        FailingCostaClient() { super(new RestTemplate(), endpoints()); }
        @Override public ResponseEntity<String> costaInternalGet(String pathAndQuery) {
            throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
