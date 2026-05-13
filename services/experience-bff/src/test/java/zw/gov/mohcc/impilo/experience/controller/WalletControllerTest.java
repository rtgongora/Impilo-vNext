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
import zw.gov.mohcc.impilo.experience.config.BffWalletProperties;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the doctrine-correct wallet local-fallback gate
 * (audit gaps <strong>G-5</strong> and <strong>G-5.1</strong>;
 * doctrine: MusheX gateway-neutrality, <em>Wallet local-fallback principle</em>).
 *
 * <p>Coverage:</p>
 * <ul>
 *   <li>Upstream wallet succeeds → unchanged 200 response (applies to
 *       {@code GET /me}, {@code GET /me/balance}, {@code GET /me/transactions},
 *       {@code GET /me/funding-sources}, and the {@code POST /pay}
 *       {@code MUSHE_WALLET} branch).</li>
 *   <li>Upstream unavailable + {@code allow-local-fallback=false} → 503 with
 *       stable code {@code WALLET_UPSTREAM_UNAVAILABLE} on all five flows.</li>
 *   <li>Upstream unavailable + {@code allow-local-fallback=true} → existing
 *       in-memory fallback shape (preserves dev/test behaviour) on all five
 *       flows.</li>
 *   <li>Non-wallet payment methods (e.g. {@code CASH}) are unaffected by the
 *       gate (the gate only fires when {@code MusheWalletServiceClient} fails).</li>
 *   <li>Upstream returning {@code null} (wallet not found) is treated as a
 *       failure mode for the gate, matching the existing behavioural contract.</li>
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
        WalletController controller = new WalletController(stub, fallbackDisabled());

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
        WalletController controller = new WalletController(stub, fallbackDisabled());

        ResponseEntity<Map<String, Object>> response = controller.getMyWallet(REQ, CORR, ACTOR);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertWalletUpstreamUnavailable(response.getBody());
    }

    @Test
    void getMyWallet_upstreamReturnsNull_fallbackDisabled_returns503WithStableCode() {
        StubMusheWalletClient stub = StubMusheWalletClient.returningNull();
        WalletController controller = new WalletController(stub, fallbackDisabled());

        ResponseEntity<Map<String, Object>> response = controller.getMyWallet(REQ, CORR, ACTOR);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertWalletUpstreamUnavailable(response.getBody());
    }

    @Test
    void getMyWallet_upstreamThrows_fallbackEnabled_returnsLocalShape() {
        StubMusheWalletClient stub = StubMusheWalletClient.throwing();
        WalletController controller = new WalletController(stub, fallbackEnabled());

        ResponseEntity<Map<String, Object>> response = controller.getMyWallet(REQ, CORR, "fallback-actor-1");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        // Local fallback shape: { data: { id, type: "wallet", attributes: { … } }, meta: { … } }
        Map<?, ?> data = assertInstanceOf(Map.class, body.get("data"));
        assertEquals("wallet", data.get("type"));
        Map<?, ?> attrs = assertInstanceOf(Map.class, data.get("attributes"));
        assertEquals("fallback-actor-1", attrs.get("ownerRef"));
        assertEquals("USD", attrs.get("currency"));
    }

    // ── POST /pay (MUSHE_WALLET branch) ────────────────────────────────

    @Test
    void pay_musheWallet_upstreamSucceeds_returnsCreated() {
        StubMusheWalletClient stub = StubMusheWalletClient.returningWallet("00000000-0000-0000-0000-000000000002")
                .withDebitResponse("debit-001");
        WalletController controller = new WalletController(stub, fallbackDisabled());

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
        WalletController controller = new WalletController(stub, fallbackDisabled());

        ResponseEntity<Map<String, Object>> response = controller.pay(
                REQ, CORR, ACTOR, null,
                Map.of("method", "MUSHE_WALLET", "amount", 10.0, "reference", "INV-2"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertWalletUpstreamUnavailable(response.getBody());
    }

    @Test
    void pay_musheWallet_upstreamReturnsNull_fallbackDisabled_returns503() {
        StubMusheWalletClient stub = StubMusheWalletClient.returningNull();
        WalletController controller = new WalletController(stub, fallbackDisabled());

        ResponseEntity<Map<String, Object>> response = controller.pay(
                REQ, CORR, ACTOR, null,
                Map.of("method", "MUSHE_WALLET", "amount", 10.0, "reference", "INV-3"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertWalletUpstreamUnavailable(response.getBody());
    }

    @Test
    void pay_musheWallet_upstreamThrows_fallbackEnabled_returnsCreatedReceipt() {
        StubMusheWalletClient stub = StubMusheWalletClient.throwing();
        WalletController controller = new WalletController(stub, fallbackEnabled());

        ResponseEntity<Map<String, Object>> response = controller.pay(
                REQ, CORR, "fallback-pay-1", null,
                Map.of("method", "MUSHE_WALLET", "amount", 5.0, "reference", "INV-FB"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        Map<?, ?> data = assertInstanceOf(Map.class, body.get("data"));
        assertEquals("payment", data.get("type"));
        Map<?, ?> attrs = assertInstanceOf(Map.class, data.get("attributes"));
        assertEquals("MUSHE_WALLET", attrs.get("method"));
        assertEquals("INV-FB", attrs.get("reference"));
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
        WalletController controller = new WalletController(stub, fallbackDisabled());

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
        WalletController controller = new WalletController(stub, fallbackDisabled());

        ResponseEntity<Map<String, Object>> response = controller.getBalance(REQ, CORR, ACTOR);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertWalletUpstreamUnavailable(response.getBody());
    }

    @Test
    void getBalance_upstreamThrows_fallbackEnabled_returnsLocalShape() {
        StubMusheWalletClient stub = StubMusheWalletClient.throwing();
        WalletController controller = new WalletController(stub, fallbackEnabled());

        ResponseEntity<Map<String, Object>> response = controller.getBalance(REQ, CORR, "fallback-balance-1");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        // Pre-Stage-3.1.1 local shape: { data: { balance, availableBalance, currency, lastUpdated }, meta }
        Map<?, ?> data = assertInstanceOf(Map.class, body.get("data"));
        assertEquals("USD", data.get("currency"));
        assertNotNull(data.get("balance"));
        assertNotNull(data.get("availableBalance"));
        assertNotNull(data.get("lastUpdated"));
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
        WalletController controller = new WalletController(stub, fallbackDisabled());

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
        WalletController controller = new WalletController(stub, fallbackDisabled());

        ResponseEntity<Map<String, Object>> response = controller.getTransactions(REQ, CORR, ACTOR);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertWalletUpstreamUnavailable(response.getBody());
    }

    @Test
    void getTransactions_upstreamThrows_fallbackEnabled_returnsLocalShape() {
        StubMusheWalletClient stub = StubMusheWalletClient.throwing();
        WalletController controller = new WalletController(stub, fallbackEnabled());

        ResponseEntity<Map<String, Object>> response = controller.getTransactions(
                REQ, CORR, "fallback-txn-1");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        // Pre-Stage-3.1.1 local shape: { data: List<Map>, meta }. With a
        // never-seen actor the in-memory TRANSACTIONS list is empty.
        List<?> data = assertInstanceOf(List.class, body.get("data"));
        assertTrue(data.isEmpty(), "local transaction list must be empty for a fresh actor");
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
        WalletController controller = new WalletController(stub, fallbackDisabled());

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
        WalletController controller = new WalletController(stub, fallbackDisabled());

        ResponseEntity<Map<String, Object>> response = controller.getFundingSources(REQ, CORR, ACTOR);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertWalletUpstreamUnavailable(response.getBody());
    }

    @Test
    void getFundingSources_upstreamThrows_fallbackEnabled_returnsLocalShape() {
        StubMusheWalletClient stub = StubMusheWalletClient.throwing();
        WalletController controller = new WalletController(stub, fallbackEnabled());

        ResponseEntity<Map<String, Object>> response = controller.getFundingSources(
                REQ, CORR, "fallback-funding-1");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        // Pre-Stage-3.1.1 local shape: { data: List<…>, meta }. With a
        // never-seen actor the wallet entry is absent so sources is empty.
        List<?> data = assertInstanceOf(List.class, body.get("data"));
        assertTrue(data.isEmpty(), "local funding-sources list must be empty for a fresh actor");
    }

    // ── Non-wallet payment methods are unaffected by the gate ─────────

    @Test
    void pay_cashMethod_unaffectedByGate_returnsCreatedEvenWhenFallbackDisabled() {
        // CASH never touches MusheWalletServiceClient — the 503 gate must NOT fire.
        StubMusheWalletClient stub = StubMusheWalletClient.throwing();
        WalletController controller = new WalletController(stub, fallbackDisabled());

        ResponseEntity<Map<String, Object>> response = controller.pay(
                REQ, CORR, ACTOR, null,
                Map.of("method", "CASH", "amount", 3.5, "reference", "INV-CASH"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        // No error envelope — receipt was produced for the non-wallet path.
        assertNull(body.get("error"));
    }

    // ── Validation paths are untouched by the gate ────────────────────

    @Test
    void pay_zeroAmount_stillRejectedAs400_validationBeforeGate() {
        StubMusheWalletClient stub = StubMusheWalletClient.throwing();
        WalletController controller = new WalletController(stub, fallbackDisabled());

        ResponseEntity<Map<String, Object>> response = controller.pay(
                REQ, CORR, ACTOR, null,
                Map.of("method", "MUSHE_WALLET", "amount", 0.0));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        Map<?, ?> error = assertInstanceOf(Map.class, body.get("error"));
        assertEquals("VALIDATION", error.get("code"));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static BffWalletProperties fallbackDisabled() {
        BffWalletProperties props = new BffWalletProperties();
        props.setAllowLocalFallback(false);
        return props;
    }

    private static BffWalletProperties fallbackEnabled() {
        BffWalletProperties props = new BffWalletProperties();
        props.setAllowLocalFallback(true);
        return props;
    }

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
