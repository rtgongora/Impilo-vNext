package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FinanceControllerTest {

    @Test
    void listBillingReturnsBadGatewayWhenCostaUnavailable() {
        FinanceController controller = new FinanceController(new FailingCostaClient(), new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.listBilling(
                "tenant-1", "req-0", "corr-0", 0, 25, null);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COSTA_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void createPaymentIntentWithoutAmountFailsClosedWhenBillCannotBeLoaded() {
        // Amount omitted → server derivation path → bill lookup fails → fail-close.
        FinanceController controller = new FinanceController(new FailingCostaClient(), new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.createPaymentIntent(
                "tenant-1", "req-fin-1", "corr-fin-1", "BILL-1", Map.of("paymentType", "FULL"));

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COSTA_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void createPaymentIntentDerivesShortfallFromPatientPayableForRemainder() {
        RecordingCostaClient costa = new RecordingCostaClient(bill(120.0, 80.0, 40.0));
        FinanceController controller = new FinanceController(costa, new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.createPaymentIntent(
                "tenant-1", "req-fin-1b", "corr-fin-1b", "BILL-1", Map.of("paymentType", "REMAINDER"));

        assertEquals(201, response.getStatusCode().value());
        assertEquals("40.00", costa.lastIntentAmount);
        assertEquals("REMAINDER", costa.lastIntentPaymentType);
        assertEquals("SERVER_DERIVED_PATIENT_PAYABLE",
                ((Map<?, ?>) response.getBody().get("meta")).get("amount_source"));
    }

    @Test
    void createPaymentIntentDerivesTotalPayableForFull() {
        RecordingCostaClient costa = new RecordingCostaClient(bill(120.0, 0.0, 120.0));
        FinanceController controller = new FinanceController(costa, new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.createPaymentIntent(
                "tenant-1", "req-fin-1c", "corr-fin-1c", "BILL-1", Map.of("paymentType", "FULL"));

        assertEquals(201, response.getStatusCode().value());
        assertEquals("120.00", costa.lastIntentAmount);
        assertEquals("SERVER_DERIVED_TOTAL_PAYABLE",
                ((Map<?, ?>) response.getBody().get("meta")).get("amount_source"));
    }

    @Test
    void createPaymentIntentRejectsDerivationWhenNothingPayable() {
        // Fully covered bill: patientPayable is zero — no silent zero-amount intents.
        RecordingCostaClient costa = new RecordingCostaClient(bill(120.0, 120.0, 0.0));
        FinanceController controller = new FinanceController(costa, new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.createPaymentIntent(
                "tenant-1", "req-fin-1d", "corr-fin-1d", "BILL-1", Map.of("paymentType", "REMAINDER"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("NOTHING_PAYABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        assertEquals(0, costa.intentCalls, "no intent may be created for a zero shortfall");
    }

    @Test
    void createPaymentIntentKeepsCallerSuppliedAmount() {
        RecordingCostaClient costa = new RecordingCostaClient(bill(120.0, 80.0, 40.0));
        FinanceController controller = new FinanceController(costa, new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.createPaymentIntent(
                "tenant-1", "req-fin-1e", "corr-fin-1e", "BILL-1",
                Map.of("paymentType", "REMAINDER", "amount", "15.00"));

        assertEquals(201, response.getStatusCode().value());
        assertEquals("15.00", costa.lastIntentAmount);
        assertEquals("CALLER", ((Map<?, ?>) response.getBody().get("meta")).get("amount_source"));
    }

    @Test
    void createPaymentIntentFailClosesWhenCostaUnavailable() {
        FinanceController controller = new FinanceController(new FailingCostaClient(), new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.createPaymentIntent(
                "tenant-1", "req-fin-2", "corr-fin-2", "BILL-1", Map.of("amount", "10.00"));

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COSTA_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void getBillingDetailFailClosesWhenCostaUnavailable() {
        FinanceController controller = new FinanceController(new FailingCostaClient(), new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.getBillingDetail(
                "tenant-1", "req-fin-3", "corr-fin-3", "BILL-404");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COSTA_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static JsonNode bill(double totalPayable, double insurerPayable, double patientPayable) {
        var node = new ObjectMapper().createObjectNode();
        var bill = node.putObject("bill");
        bill.put("billId", "BILL-1");
        bill.put("status", "FINAL");
        bill.put("totalPayable", totalPayable);
        bill.put("insurerPayable", insurerPayable);
        bill.put("patientPayable", patientPayable);
        bill.put("currency", "USD");
        return node;
    }

    private static final class RecordingCostaClient extends CostaServiceClient {
        private final JsonNode billPayload;
        int intentCalls = 0;
        String lastIntentAmount;
        String lastIntentPaymentType;

        private RecordingCostaClient(JsonNode billPayload) {
            super(new RestTemplate(), endpoints());
            this.billPayload = billPayload;
        }

        @Override
        public JsonNode getBill(String billId) {
            return billPayload;
        }

        @Override
        public JsonNode createPaymentIntent(String billId, String paymentType, String amount) {
            intentCalls++;
            lastIntentPaymentType = paymentType;
            lastIntentAmount = amount;
            var payment = new ObjectMapper().createObjectNode();
            payment.put("id", 1);
            payment.put("billId", billId);
            payment.put("paymentType", paymentType);
            payment.put("amount", Double.parseDouble(amount));
            payment.put("status", "PENDING");
            return payment;
        }
    }

    private static final class FailingCostaClient extends CostaServiceClient {
        private FailingCostaClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listBills(int page, int size, String status) {
            throw new RuntimeException("costa unavailable");
        }

        @Override
        public JsonNode createPaymentIntent(String billId, String paymentType, String amount) {
            throw new RuntimeException("costa unavailable");
        }

        @Override
        public JsonNode getBill(String billId) {
            throw new RuntimeException("costa unavailable");
        }
    }
}
