package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MarketplaceFlowServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OF-B7 — comparison/selection proxy contract:
 * verbatim payload passthrough, coded-envelope preservation on domain
 * rejections, transport failure → coded 502, idempotency-key passthrough.
 */
class MarketplaceRequestBffControllerTest {

    private static final String OFFERS_PAYLOAD =
            "{\"success\":true,\"data\":[{\"offerId\":\"OF-1\",\"rankedBecause\":[\"SUITABLE\",\"CHEAPEST\"],"
                    + "\"financials\":{\"status\":\"VERIFIED\",\"patientLiability\":12.50,\"estimateNeverFinal\":true}}]}";

    private static final String SELECT_PAYLOAD =
            "{\"success\":true,\"data\":{\"selectionId\":\"SEL-1\",\"outcomeCode\":\"COMMITTED\",\"committed\":true}}";

    @Test
    void listOffersForwardsRankedPayloadWithMetaHeaders() {
        MarketplaceRequestBffController controller = new MarketplaceRequestBffController(new StubClient());

        ResponseEntity<String> response = controller.listOffers("MR-1", "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("corr-1", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals(OFFERS_PAYLOAD, response.getBody());
    }

    @Test
    void selectForwardsBodyAndIdempotencyKeyVerbatim() {
        StubClient client = new StubClient();
        MarketplaceRequestBffController controller = new MarketplaceRequestBffController(client);

        ResponseEntity<String> response = controller.select(
                "MR-1", "{\"offerId\":\"OF-1\"}", "idem-key-1", "req-2", "corr-2");

        assertEquals(201, response.getStatusCode().value());
        assertEquals(SELECT_PAYLOAD, response.getBody());
        assertEquals("{\"offerId\":\"OF-1\"}", client.lastSelectBody);
        assertEquals("idem-key-1", client.lastIdempotencyKey);
    }

    @Test
    void codedDomainRejectionPassesThroughWithRealStatusAndEnvelope() {
        String envelope = "{\"success\":false,\"error\":{\"code\":\"REQUEST_NOT_SELECTABLE\","
                + "\"message\":\"Selection window closed\",\"status\":409}}";
        MarketplaceRequestBffController controller =
                new MarketplaceRequestBffController(new FailingClient(
                        HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict",
                                jsonHeaders(), envelope.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

        ResponseEntity<String> response = controller.select(
                "MR-1", "{\"offerId\":\"OF-1\"}", "idem-key-2", "req-3", "corr-3");

        assertEquals(409, response.getStatusCode().value());
        assertEquals(envelope, response.getBody());
        assertEquals("corr-3", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
    }

    @Test
    void transportFailureBecomesCoded502NotAMaskedOutcome() {
        MarketplaceRequestBffController controller =
                new MarketplaceRequestBffController(new FailingClient(
                        new ResourceAccessException("Connection refused")));

        ResponseEntity<String> response = controller.listOffers("MR-1", "req-4", "corr-4");

        assertEquals(502, response.getStatusCode().value());
        assertTrue(response.getBody().contains("MSIKA_FLOW_UNAVAILABLE"));
        assertEquals("req-4", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
    }

    @Test
    void pathwayElectionForwardsPutPayload() {
        StubClient client = new StubClient();
        MarketplaceRequestBffController controller = new MarketplaceRequestBffController(client);

        ResponseEntity<String> response = controller.electPathway(
                "MR-1", "{\"pathway\":\"DELIVERY\",\"deliveryAddress\":\"12 Main St\"}", "req-5", "corr-5");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"fulfilmentPathway\":\"DELIVERY\""));
        assertEquals("{\"pathway\":\"DELIVERY\",\"deliveryAddress\":\"12 Main St\"}", client.lastPathwayBody);
    }

    @Test
    void cancelToleratesAbsentBody() {
        MarketplaceRequestBffController controller = new MarketplaceRequestBffController(new StubClient());

        ResponseEntity<String> response = controller.cancel("MR-1", null, "req-6", "corr-6");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("CANCELLED"));
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static class StubClient extends MarketplaceFlowServiceClient {
        String lastSelectBody;
        String lastIdempotencyKey;
        String lastPathwayBody;

        StubClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> getRequest(String requestId) {
            return json(200, "{\"success\":true,\"data\":{\"requestId\":\"" + requestId + "\",\"status\":\"OFFERS_AVAILABLE\"}}");
        }

        @Override
        public ResponseEntity<String> listOffers(String requestId) {
            return json(200, OFFERS_PAYLOAD);
        }

        @Override
        public ResponseEntity<String> select(String requestId, String body, String idempotencyKey) {
            this.lastSelectBody = body;
            this.lastIdempotencyKey = idempotencyKey;
            return json(201, SELECT_PAYLOAD);
        }

        @Override
        public ResponseEntity<String> selectSplit(String requestId, String body, String idempotencyKey) {
            this.lastSelectBody = body;
            this.lastIdempotencyKey = idempotencyKey;
            return json(200, "{\"success\":true,\"data\":{\"outcome\":\"PARTIAL_COMMIT\"}}");
        }

        @Override
        public ResponseEntity<String> electPathway(String requestId, String body) {
            this.lastPathwayBody = body;
            return json(200, "{\"success\":true,\"data\":{\"requestId\":\"" + requestId
                    + "\",\"fulfilmentPathway\":\"DELIVERY\",\"deliveryDetailsPresent\":true}}");
        }

        @Override
        public ResponseEntity<String> cancel(String requestId, String body) {
            return json(200, "{\"success\":true,\"data\":{\"requestId\":\"" + requestId + "\",\"status\":\"CANCELLED\"}}");
        }

        private static ResponseEntity<String> json(int status, String body) {
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
        }
    }

    private static final class FailingClient extends MarketplaceFlowServiceClient {
        private final RuntimeException failure;

        FailingClient(RuntimeException failure) {
            super(new RestTemplate(), endpoints());
            this.failure = failure;
        }

        @Override
        public ResponseEntity<String> getRequest(String requestId) { throw failure; }

        @Override
        public ResponseEntity<String> listOffers(String requestId) { throw failure; }

        @Override
        public ResponseEntity<String> select(String requestId, String body, String idempotencyKey) { throw failure; }

        @Override
        public ResponseEntity<String> selectSplit(String requestId, String body, String idempotencyKey) { throw failure; }

        @Override
        public ResponseEntity<String> electPathway(String requestId, String body) { throw failure; }

        @Override
        public ResponseEntity<String> cancel(String requestId, String body) { throw failure; }
    }
}
