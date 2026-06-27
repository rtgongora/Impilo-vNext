package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.MusheWalletServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the doctrine-correct wallet fail-clean contract
 * (audit gaps <strong>G-5</strong> and <strong>G-5.1</strong>;
 * doctrine: MusheX gateway-neutrality, <em>BFF is not a source of truth for
 * financial state</em>).
 *
 * <p>The stateless BFF NEVER fabricates wallet state. The former
 * {@code impilo.wallet.allow-local-fallback} in-memory fabrication has been
 * removed: mushe-wallet-service is the sole owner and the BFF only proxies.</p>
 *
 * <p>Coverage:</p>
 * <ul>
 *   <li>Upstream wallet succeeds → unchanged 200 response (applies to
 *       {@code GET /me}, {@code GET /me/balance}, {@code GET /me/transactions},
 *       {@code GET /me/funding-sources}, and the {@code POST /pay}
 *       {@code MUSHE_WALLET} branch).</li>
 *   <li>Upstream unavailable (exception or empty response) → 503 with stable
 *       code {@code WALLET_UPSTREAM_UNAVAILABLE} on all five flows — never a
 *       fabricated balance/transaction/funding-source.</li>
 *   <li>Non-wallet payment methods (e.g. {@code CASH}) fail closed with
 *       {@code 501} rather than synthesizing a successful transaction.</li>
 *   <li>Upstream returning {@code null} (wallet not found) is treated as a
 *       failure mode, matching the fail-clean contract.</li>
 * </ul>
 */
class WalletControllerTest {

    private static final String REQ = "req-w-1";
    private static final String CORR = "corr-w-1";
    private static final String ACTOR = "actor-1";

    // ── GET /me ────────────────────────────────────────────────────────

    @Test
    void getMyWallet_upstreamSucceeds_returnsUpstreamPayloadUnchanged() {
        StubMusheWalletClient stub = StubMusheWalletClient.returningWallet("00000000-0000-0000-0000-000000000001");
        WalletController controller = new WalletController(stub);

        ResponseEntity<Map<String, Object>> response = controller.getMyWallet(REQ, CORR, ACTOR);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        JsonNode data = assertInstanceOf(JsonNode.class, body.get("data"));
        assertEquals("00000000-0000-0000-0000-000000000001", data.get("id").asText());
        assertEquals(REQ, ((Map<?, ?>) body.get("meta")).get("request_id"));
    }

    @Test
    void getMyWallet_upstreamThrows_fallbackDisabled_returns503WithStableCode() {
        StubMusheWalletClient stub = StubMusheWalletClient.throwing();
        WalletController controller = new WalletController(stub);

        ResponseEntity<Map<String, Object>> response = controller.getMyWallet(REQ, CORR, ACTOR);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertWalletUpstreamUnavailable(response.getBody());
    }

    @Test
    void getMyWallet_upstreamReturnsNull_fallbackDisabled_returns503WithStableCode() {
        StubMusheWalletClient stub = StubMusheWalletClient.returningNull();
        WalletController controller = new WalletController(stub);

        ResponseEntity<Map<String, Object>> response = controller.getMyWallet(REQ, CORR, ACTOR);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertWalletUpstreamUnavailable(response.getBody());
    }

    // ── POST /pay (MUSHE_WALLET branch) ────────────────────────────────

    @Test
    void pay_musheWallet_upstreamSucceeds_returnsCreated() {
        StubMusheWalletClient stub = StubMusheWalletClient.returningWallet("00000000-0000-0000-0000-000000000002")
                .withDebitResponse("debit-001");
        WalletController controller = new WalletController(stub);

        ResponseEntity<Map<String, Object>> response = controller.pay(
                REQ, CORR, ACTOR, null,
                Map.of("method", "MUSHE_WALLET", "amount", 10.0, "reference", "INV-1"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        JsonNode debit = assertInstanceOf(JsonNode.class, body.get("data"));
        assertEquals("debit-001", debit.get("transactionId").asText());
    }

    @Test
    void pay_musheWallet_upstreamThrows_fallbackDisabled_returns503() {
        StubMusheWalletClient stub = StubMusheWalletClient.throwing();
        WalletController controller = new WalletController(stub);

        ResponseEntity<Map<String, Object>> response = controller.pay(
                REQ, CORR, ACTOR, null,
                Map.of("method", "MUSHE_WALLET", "amount", 10.0, "reference", "INV-2"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertWalletUpstreamUnavailable(response.getBody());
    }

    @Test
    void pay_musheWallet_upstreamReturnsNull_fallbackDisabled_returns503() {
        StubMusheWalletClient stub = StubMusheWalletClient.returningNull();
        WalletController controller = new WalletController(stub);

        ResponseEntity<Map<String, Object>> response = controller.pay(
                REQ, CORR, ACTOR, null,
                Map.of("method", "MUSHE_WALLET", "amount", 10.0, "reference", "INV-3"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertWalletUpstreamUnavailable(response.getBody());
    }

    // ── GET /me/balance (G-5.1) ───────────────────────────────────────

    @Test
    void getBalance_upstreamSucceeds_returnsUpstreamPayloadUnchanged() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode balanceNode = mapper.createObjectNode()
                .put("currentBalance", 142.50)
                .put("availableBalance", 142.50)
                .put("holdBalance", 0.0)
                .put("currency", "USD");
        StubMusheWalletClient stub = StubMusheWalletClient
                .returningWallet("00000000-0000-0000-0000-000000000010")
                .withBalance(balanceNode);
        WalletController controller = new WalletController(stub);

        ResponseEntity<Map<String, Object>> response = controller.getBalance(REQ, CORR, ACTOR);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        JsonNode data = assertInstanceOf(JsonNode.class, body.get("data"));
        assertEquals(142.50, data.get("currentBalance").asDouble());
        assertEquals("USD", data.get("currency").asText());
        assertEquals(REQ, ((Map<?, ?>) body.get("meta")).get("request_id"));
    }

    @Test
    void getBalance_upstreamThrows_fallbackDisabled_returns503WithStableCode() {
        StubMusheWalletClient stub = StubMusheWalletClient.throwing();
        WalletController controller = new WalletController(stub);

        ResponseEntity<Map<String, Object>> response = controller.getBalance(REQ, CORR, ACTOR);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertWalletUpstreamUnavailable(response.getBody());
    }

    // ── GET /me/transactions (G-5.1) ──────────────────────────────────

    @Test
    void getTransactions_upstreamSucceeds_returnsUpstreamPayloadUnchanged() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode paginated = mapper.createObjectNode();
        paginated.put("totalElements", 1);
        paginated.putArray("content")
                .addObject()
                .put("id", "txn-upstream-1")
                .put("amount", 12.34)
                .put("status", "COMPLETED");
        StubMusheWalletClient stub = StubMusheWalletClient
                .returningWallet("00000000-0000-0000-0000-000000000011")
                .withTransactions(paginated);
        WalletController controller = new WalletController(stub);

        ResponseEntity<Map<String, Object>> response = controller.getTransactions(REQ, CORR, ACTOR);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        JsonNode data = assertInstanceOf(JsonNode.class, body.get("data"));
        assertEquals(1, data.get("totalElements").asInt());
        assertEquals("txn-upstream-1", data.get("content").get(0).get("id").asText());
    }

    @Test
    void getTransactions_upstreamThrows_fallbackDisabled_returns503WithStableCode() {
        StubMusheWalletClient stub = StubMusheWalletClient.throwing();
        WalletController controller = new WalletController(stub);

        ResponseEntity<Map<String, Object>> response = controller.getTransactions(REQ, CORR, ACTOR);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertWalletUpstreamUnavailable(response.getBody());
    }

    // ── GET /me/funding-sources (G-5.1) ───────────────────────────────

    @Test
    void getFundingSources_upstreamSucceeds_returnsUpstreamPayloadUnchanged() {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode sourcesNode = mapper.createArrayNode();
        sourcesNode.addObject()
                .put("id", "fs-upstream-1")
                .put("sourceType", "MOBILE_MONEY")
                .put("provider", "EcoCash")
                .put("status", "VERIFIED");
        StubMusheWalletClient stub = StubMusheWalletClient
                .returningWallet("00000000-0000-0000-0000-000000000012")
                .withFundingSources(sourcesNode);
        WalletController controller = new WalletController(stub);

        ResponseEntity<Map<String, Object>> response = controller.getFundingSources(REQ, CORR, ACTOR);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        JsonNode data = assertInstanceOf(JsonNode.class, body.get("data"));
        assertTrue(data.isArray(), "upstream funding-sources payload must remain an array");
        assertEquals("fs-upstream-1", data.get(0).get("id").asText());
    }

    @Test
    void getFundingSources_upstreamThrows_fallbackDisabled_returns503WithStableCode() {
        StubMusheWalletClient stub = StubMusheWalletClient.throwing();
        WalletController controller = new WalletController(stub);

        ResponseEntity<Map<String, Object>> response = controller.getFundingSources(REQ, CORR, ACTOR);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertWalletUpstreamUnavailable(response.getBody());
    }

    // ── Non-wallet payment methods are unaffected by the gate ─────────

    @Test
    void pay_cashMethodReturnsNotImplementedUntilRailIsWired() {
        StubMusheWalletClient stub = StubMusheWalletClient.throwing();
        WalletController controller = new WalletController(stub);

        ResponseEntity<Map<String, Object>> response = controller.pay(
                REQ, CORR, ACTOR, null,
                Map.of("method", "CASH", "amount", 3.5, "reference", "INV-CASH"));

        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        Map<?, ?> error = assertInstanceOf(Map.class, body.get("error"));
        assertEquals("PAYMENT_METHOD_UNAVAILABLE", error.get("code"));
    }

    // ── Validation paths are untouched by the gate ────────────────────

    @Test
    void pay_zeroAmount_stillRejectedAs400_validationBeforeGate() {
        StubMusheWalletClient stub = StubMusheWalletClient.throwing();
        WalletController controller = new WalletController(stub);

        ResponseEntity<Map<String, Object>> response = controller.pay(
                REQ, CORR, ACTOR, null,
                Map.of("method", "MUSHE_WALLET", "amount", 0.0));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        Map<?, ?> error = assertInstanceOf(Map.class, body.get("error"));
        assertEquals("VALIDATION", error.get("code"));
        Map<?, ?> meta = assertInstanceOf(Map.class, body.get("meta"));
        assertEquals(REQ, meta.get("request_id"));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static void assertWalletUpstreamUnavailable(Map<String, Object> body) {
        assertNotNull(body, "503 body must not be null");
        Map<?, ?> error = assertInstanceOf(Map.class, body.get("error"));
        assertEquals(WalletController.UPSTREAM_UNAVAILABLE_CODE, error.get("code"));
        assertTrue(error.get("message") instanceof String s && !s.isBlank(),
                "503 must carry a non-blank human-readable message");
        Map<?, ?> meta = assertInstanceOf(Map.class, body.get("meta"));
        assertEquals(REQ, meta.get("request_id"));
        assertEquals(CORR, meta.get("correlation_id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    /**
     * Stub subclass of {@link MusheWalletServiceClient} that lets each test
     * choose how {@code getWalletByOwner}, {@code debitWallet},
     * {@code getBalance}, {@code getTransactions}, and
     * {@code listFundingSources} behave without standing up a real HTTP
     * server. Matches the existing test pattern used by
     * {@code SummaryProxyControllerTest} and {@code ReconciliationControllerTest}.
     *
     * <p>The balance / transactions / funding-sources overrides default to
     * a {@link IllegalStateException}-throwing supplier so any test that
     * forgets to configure them surfaces the omission immediately rather
     * than silently returning {@code null}.</p>
     */
    private static final class StubMusheWalletClient extends MusheWalletServiceClient {
        private final Supplier<JsonNode> walletSupplier;
        private String debitTransactionId = "debit-default";
        private Supplier<JsonNode> balanceSupplier = () -> {
            throw new IllegalStateException("balance supplier not configured");
        };
        private Supplier<JsonNode> transactionsSupplier = () -> {
            throw new IllegalStateException("transactions supplier not configured");
        };
        private Supplier<JsonNode> fundingSourcesSupplier = () -> {
            throw new IllegalStateException("funding-sources supplier not configured");
        };

        private StubMusheWalletClient(Supplier<JsonNode> walletSupplier) {
            super(new RestTemplate(), endpoints(), new ObjectMapper());
            this.walletSupplier = walletSupplier;
        }

        static StubMusheWalletClient returningWallet(String walletId) {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode wallet = mapper.createObjectNode()
                    .put("id", walletId)
                    .put("ownerType", "PERSON")
                    .put("ownerRef", "actor-1")
                    .put("currency", "USD");
            return new StubMusheWalletClient(() -> wallet);
        }

        static StubMusheWalletClient returningNull() {
            return new StubMusheWalletClient(() -> null);
        }

        static StubMusheWalletClient throwing() {
            return new StubMusheWalletClient(() -> {
                throw new IllegalStateException("simulated upstream wallet outage");
            });
        }

        StubMusheWalletClient withDebitResponse(String transactionId) {
            this.debitTransactionId = transactionId;
            return this;
        }

        StubMusheWalletClient withBalance(JsonNode balance) {
            this.balanceSupplier = () -> balance;
            return this;
        }

        StubMusheWalletClient withTransactions(JsonNode transactions) {
            this.transactionsSupplier = () -> transactions;
            return this;
        }

        StubMusheWalletClient withFundingSources(JsonNode sources) {
            this.fundingSourcesSupplier = () -> sources;
            return this;
        }

        @Override
        public JsonNode getWalletByOwner(String ownerType, String ownerRef) {
            return walletSupplier.get();
        }

        @Override
        public JsonNode debitWallet(UUID walletId, Map<String, Object> body) {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.createObjectNode()
                    .put("transactionId", debitTransactionId)
                    .put("walletId", walletId.toString())
                    .put("status", "COMPLETED");
        }

        @Override
        public JsonNode getBalance(UUID walletId) {
            return balanceSupplier.get();
        }

        @Override
        public JsonNode getTransactions(UUID walletId, int page, int size) {
            return transactionsSupplier.get();
        }

        @Override
        public JsonNode listFundingSources(UUID walletId) {
            return fundingSourcesSupplier.get();
        }
    }
}
