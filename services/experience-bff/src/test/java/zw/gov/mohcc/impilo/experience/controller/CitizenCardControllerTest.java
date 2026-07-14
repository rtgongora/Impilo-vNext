package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.MusheWalletServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the citizen SMART-card BFF proxy: it wraps upstream mushe responses in the BFF envelope,
 * binds bill-contribution beneficiary to the caller's wallet, propagates an upstream rejection status
 * (e.g. a fail-closed 422 biometric-pay reject) honestly rather than masking it, and fails clean when
 * the caller's wallet cannot be resolved.
 */
class CitizenCardControllerTest {

    private static final String REQ = "req-c-1";
    private static final String CORR = "corr-c-1";
    private static final String ACTOR = "actor-1";

    @Test
    void payBiometric_upstreamSucceeds_wrapsInEnvelope() {
        Stub stub = new Stub();
        stub.biometricResult = obj().put("txnId", "txn-bio-1").put("status", "COMPLETED");
        CitizenCardController controller = new CitizenCardController(stub, new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.payBiometric(
                Map.of("vitoCardNumber", "VITO-1", "payload", "{...}", "signature", "sig"), REQ, CORR);

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = assertInstanceOf(JsonNode.class, response.getBody().get("data"));
        assertEquals("txn-bio-1", data.get("txnId").asText());
    }

    @Test
    void payBiometric_upstreamRejects422_isPropagatedNotMaskedAs503() {
        Stub stub = new Stub();
        stub.biometricError = HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_ENTITY,
                "Unprocessable Entity", org.springframework.http.HttpHeaders.EMPTY,
                "{\"error\":{\"code\":\"OFFLINE_TXN_REJECTED\"}}".getBytes(), null);
        CitizenCardController controller = new CitizenCardController(stub, new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.payBiometric(
                Map.of("vitoCardNumber", "VITO-1", "payload", "{}", "signature", "bad"), REQ, CORR);

        // The fail-closed biometric reject must reach the client as 422, not a generic 503.
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        Map<?, ?> error = assertInstanceOf(Map.class, response.getBody().get("error"));
        assertEquals("UPSTREAM_REJECTED", error.get("code"));
    }

    @Test
    void createContribution_bindsBeneficiaryWalletToCaller() {
        Stub stub = new Stub();
        stub.wallet = obj().put("id", "00000000-0000-0000-0000-000000000009");
        CitizenCardController controller = new CitizenCardController(stub, new ObjectMapper());

        controller.createContribution(Map.of("title", "Hospital bill", "beneficiaryWalletId", "SPOOFED"),
                REQ, CORR, ACTOR);

        // The controller must overwrite any client-supplied beneficiary with the resolved caller wallet.
        assertEquals("00000000-0000-0000-0000-000000000009", stub.createdWith.get("beneficiaryWalletId"));
        assertEquals(ACTOR, stub.createdWith.get("createdBy"));
    }

    @Test
    void myCards_walletUnavailable_failsClean503() {
        Stub stub = new Stub(); // wallet resolution throws
        stub.walletThrows = true;
        CitizenCardController controller = new CitizenCardController(stub, new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.myCards(REQ, CORR, ACTOR);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        Map<?, ?> error = assertInstanceOf(Map.class, response.getBody().get("error"));
        assertEquals("WALLET_UPSTREAM_UNAVAILABLE", error.get("code"));
    }

    @Test
    void setPhrCarry_missingEnabled_isRejected400() {
        CitizenCardController controller = new CitizenCardController(new Stub(), new ObjectMapper());
        ResponseEntity<Map<String, Object>> response = controller.setPhrCarry(
                UUID.randomUUID(), Map.of(), REQ, CORR);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    private static ObjectNode obj() {
        return new ObjectMapper().createObjectNode();
    }

    /** Minimal stub of the mushe client — only the methods CitizenCardController calls. */
    private static final class Stub extends MusheWalletServiceClient {
        JsonNode wallet;
        boolean walletThrows;
        JsonNode biometricResult;
        RuntimeException biometricError;
        Map<String, Object> createdWith;

        Stub() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), new ObjectMapper());
        }

        @Override
        public JsonNode getWalletByOwner(String ownerType, String ownerRef) {
            if (walletThrows) {
                throw new IllegalStateException("simulated wallet outage");
            }
            return wallet;
        }

        @Override
        public JsonNode getCardsByWallet(UUID walletId) {
            return new ObjectMapper().createObjectNode().put("walletId", walletId.toString());
        }

        @Override
        public JsonNode redeemBiometricTransaction(Map<String, Object> body) {
            if (biometricError != null) {
                throw biometricError;
            }
            return biometricResult;
        }

        @Override
        public JsonNode createBillContribution(Map<String, Object> body) {
            this.createdWith = body;
            return new ObjectMapper().createObjectNode().put("shareToken", "tok-xyz");
        }
    }
}
