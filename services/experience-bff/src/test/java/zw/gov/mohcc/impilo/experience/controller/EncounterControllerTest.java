package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncounterControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static EncounterController controller(PctServiceClient pct) {
        return new EncounterController(pct, new StubClinicalClient(), new StubCostaClient());
    }

    private static EncounterController controller(PctServiceClient pct, CostaServiceClient costa) {
        return new EncounterController(pct, new StubClinicalClient(), costa);
    }

    private static EncounterController controller() {
        return controller(new StubPctClient());
    }

    @Test
    void listEncounters_returnsDataAndMeta() {
        EncounterController controller = controller();
        ResponseEntity<Map<String, Object>> response =
                controller.listEncounters("tenant-1", "req-1", "corr-1", 0, 20, "patient-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listEncounters_emptyPatientId_returnsBadRequest() {
        EncounterController controller = controller();
        ResponseEntity<Map<String, Object>> response =
                controller.listEncounters("tenant-1", "req-2", "corr-2", 0, 20, null);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("MISSING_PATIENT_ID", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void createEncounter_returns201() {
        EncounterController controller = controller();
        EncounterController.CreateEncounterRequest request = new EncounterController.CreateEncounterRequest(
                "patient-1",
                "facility-1",
                "CONSULTATION",
                "outpatient",
                "walk_in",
                "in_person",
                null,
                "facility",
                "routine",
                "P4",
                "PATH-GENERAL-01",
                "PROTO-GENERAL-01",
                "Headache",
                "journey-1",
                "cpid-1"
        );
        ResponseEntity<Map<String, Object>> response =
                controller.createEncounter("t1", "pod-1", "req-3", "corr-3", null, request);
        assertEquals(201, response.getStatusCode().value());
        Map<?, ?> meta = (Map<?, ?>) response.getBody().get("meta");
        assertEquals("req-3", meta.get("request_id"));
        assertEquals("encounter-1", meta.get("core_transaction_id"));
        assertEquals("journey-1", meta.get("journey_id"));
    }

    @Test
    void closeEncounter_usesPctCompletionAndReturnsOk() {
        EncounterController controller = controller();
        ResponseEntity<Map<String, Object>> response =
                controller.closeEncounter("1", "tenant-1", "req-4", "corr-4", null, Map.of());
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("COMPLETED", data.get("status").asText());
    }

    @Test
    void closeEncounter_triggersCostaBillDraftAndSurfacesBillId() {
        StubCostaClient costa = new StubCostaClient();
        EncounterController controller = controller(new StubPctClient(), costa);
        ResponseEntity<Map<String, Object>> response =
                controller.closeEncounter("42", "tenant-1", "req-4b", "corr-4b", null, Map.of());
        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> meta = (Map<?, ?>) response.getBody().get("meta");
        assertEquals("BILL-42", meta.get("costa_bill_id"));
        assertEquals(1, costa.draftCalls);
        assertEquals("42", costa.lastDraftEncounterId);
        assertEquals("ENCOUNTER", costa.lastDraftBillType);
    }

    @Test
    void closeEncounter_costaFailureIsNonBlocking() {
        StubCostaClient costa = new StubCostaClient();
        costa.failDraft = true;
        EncounterController controller = controller(new StubPctClient(), costa);
        ResponseEntity<Map<String, Object>> response =
                controller.closeEncounter("42", "tenant-1", "req-4c", "corr-4c", null, Map.of());
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("COMPLETED", data.get("status").asText());
        Map<?, ?> meta = (Map<?, ?>) response.getBody().get("meta");
        assertNull(meta.get("costa_bill_id"), "no fake bill id when COSTA fails");
    }

    @Test
    void closeEncounter_repeatedClose_reusesCostaDedupNotDuplicateDrafts() {
        // COSTA createDraft is idempotent per encounter (returns the existing
        // DRAFT/ACCUMULATING bill). The BFF must simply pass through the same id.
        StubCostaClient costa = new StubCostaClient();
        EncounterController controller = controller(new StubPctClient(), costa);
        ResponseEntity<Map<String, Object>> first =
                controller.closeEncounter("42", "tenant-1", "req-4d", "corr-4d", null, Map.of());
        ResponseEntity<Map<String, Object>> second =
                controller.closeEncounter("42", "tenant-1", "req-4e", "corr-4e", null, Map.of());
        assertEquals(((Map<?, ?>) first.getBody().get("meta")).get("costa_bill_id"),
                ((Map<?, ?>) second.getBody().get("meta")).get("costa_bill_id"));
    }

    @Test
    void dischargeEncounter_resolvesJourneyAndStartsDischarge() {
        EncounterController controller = controller();
        ResponseEntity<Map<String, Object>> response =
                controller.dischargeEncounter("1", "tenant-1", "req-5", "corr-5", null, Map.of("dischargeType", "CLINICAL"));
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("journey-1", data.get("journeyId").asText());
        assertEquals("INITIATED", data.get("status").asText());
    }

    @Test
    void dischargeEncounter_forwardsInstructionFieldsToPct() {
        StubPctClient pct = new StubPctClient();
        EncounterController controller = controller(pct);
        Map<String, Object> body = Map.of(
                "dischargeType", "DISCHARGE",
                "discharge_diagnosis", "Acute bronchitis",
                "follow_up_instructions", "Review in 7 days",
                "patient_instructions", "Rest and fluids");
        ResponseEntity<Map<String, Object>> response =
                controller.dischargeEncounter("1", "tenant-1", "req-5b", "corr-5b", null, body);
        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        assertEquals("Acute bronchitis", meta.get("discharge_diagnosis"));
        assertEquals("Review in 7 days", meta.get("follow_up_instructions"));
        assertEquals("Rest and fluids", meta.get("patient_instructions"));
        assertEquals("Acute bronchitis", pct.lastDischargeBody.get("dischargeDiagnosis"));
        assertEquals("Review in 7 days", pct.lastDischargeBody.get("followUpInstructions"));
        assertEquals("encounter-1", meta.get("core_transaction_id"));
        assertEquals("1", meta.get("encounter_id"));
    }

    @Test
    void dischargeEncounter_triggersCostaBillDraftInMeta() {
        StubCostaClient costa = new StubCostaClient();
        EncounterController controller = controller(new StubPctClient(), costa);
        ResponseEntity<Map<String, Object>> response =
                controller.dischargeEncounter("7", "tenant-1", "req-5c", "corr-5c", null,
                        Map.of("dischargeType", "CLINICAL"));
        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> meta = (Map<?, ?>) response.getBody().get("meta");
        assertEquals("BILL-7", meta.get("costa_bill_id"));
    }

    // ── Encounter billing visibility (read-only composition) ─────────

    @Test
    void getEncounterBilling_noBills_returnsNotBilled() {
        StubCostaClient costa = new StubCostaClient();
        EncounterController controller = controller(new StubPctClient(), costa);
        ResponseEntity<Map<String, Object>> response =
                controller.getEncounterBilling("9", "req-b1", "corr-b1");
        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("NOT_BILLED", data.get("billing_state"));
        assertTrue(((List<?>) data.get("bills")).isEmpty());
    }

    @Test
    void getEncounterBilling_finalBillWithShortfallAndNoPayments_isShortfallDue() {
        StubCostaClient costa = new StubCostaClient();
        costa.billsByEncounter = billArray(finalBill("BILL-9", 120.0, 80.0, 40.0));
        EncounterController controller = controller(new StubPctClient(), costa);
        ResponseEntity<Map<String, Object>> response =
                controller.getEncounterBilling("9", "req-b2", "corr-b2");
        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("SHORTFALL_DUE", data.get("billing_state"));
        @SuppressWarnings("unchecked")
        Map<String, Object> bill = ((List<Map<String, Object>>) data.get("bills")).get(0);
        assertEquals(40.0, bill.get("patient_payable"));
        assertEquals(80.0, bill.get("insurer_payable"));
    }

    @Test
    void getEncounterBilling_paidShortfall_isPaid_neverFakedFromPendingPayments() {
        StubCostaClient costa = new StubCostaClient();
        costa.billsByEncounter = billArray(finalBill("BILL-9", 120.0, 80.0, 40.0));

        // Pending payment must NOT produce a PAID state.
        costa.paymentsByBill.put("BILL-9", paymentsArray(payment("1", 40.0, 0.0, "PENDING")));
        ResponseEntity<Map<String, Object>> pending = controller(new StubPctClient(), costa)
                .getEncounterBilling("9", "req-b3", "corr-b3");
        @SuppressWarnings("unchecked")
        Map<String, Object> pendingData = (Map<String, Object>) pending.getBody().get("data");
        assertEquals("PAYMENT_PENDING", pendingData.get("billing_state"));

        // Only a genuinely PAID payment covering the shortfall yields PAID.
        costa.paymentsByBill.put("BILL-9", paymentsArray(payment("1", 40.0, 40.0, "PAID")));
        ResponseEntity<Map<String, Object>> paid = controller(new StubPctClient(), costa)
                .getEncounterBilling("9", "req-b4", "corr-b4");
        @SuppressWarnings("unchecked")
        Map<String, Object> paidData = (Map<String, Object>) paid.getBody().get("data");
        assertEquals("PAID", paidData.get("billing_state"));
    }

    @Test
    void getEncounterBilling_fullyCoveredBill_isCovered() {
        StubCostaClient costa = new StubCostaClient();
        costa.billsByEncounter = billArray(finalBill("BILL-9", 120.0, 120.0, 0.0));
        ResponseEntity<Map<String, Object>> response = controller(new StubPctClient(), costa)
                .getEncounterBilling("9", "req-b5", "corr-b5");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("COVERED", data.get("billing_state"));
    }

    @Test
    void getEncounterBilling_costaUnavailable_failsClosed() {
        StubCostaClient costa = new StubCostaClient();
        costa.failBillsByEncounter = true;
        ResponseEntity<Map<String, Object>> response = controller(new StubPctClient(), costa)
                .getEncounterBilling("9", "req-b6", "corr-b6");
        assertEquals(502, response.getStatusCode().value());
        assertEquals("BILLING_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static ObjectNode finalBill(String billId, double total, double insurer, double patient) {
        ObjectNode bill = mapper.createObjectNode();
        bill.put("billId", billId);
        bill.put("status", "FINAL");
        bill.put("billType", "ENCOUNTER");
        bill.put("currency", "USD");
        bill.put("totalCharge", total);
        bill.put("totalPayable", total);
        bill.put("insurerPayable", insurer);
        bill.put("patientPayable", patient);
        bill.put("coverageStatus", insurer > 0 ? "ELIGIBLE" : "");
        bill.put("coveragePlanCode", insurer > 0 ? "PLAN-STD" : "");
        bill.put("createdAt", "2026-07-05T00:00:00Z");
        return bill;
    }

    private static ArrayNode billArray(ObjectNode... bills) {
        ArrayNode arr = mapper.createArrayNode();
        for (ObjectNode bill : bills) arr.add(bill);
        return arr;
    }

    private static ObjectNode payment(String id, double amount, double paidAmount, String status) {
        ObjectNode p = mapper.createObjectNode();
        p.put("id", id);
        p.put("paymentType", "REMAINDER");
        p.put("amount", amount);
        p.put("paidAmount", paidAmount);
        p.put("status", status);
        p.put("mushexPaymentIntentId", "INT-" + id);
        return p;
    }

    private static ArrayNode paymentsArray(ObjectNode... payments) {
        ArrayNode arr = mapper.createArrayNode();
        for (ObjectNode p : payments) arr.add(p);
        return arr;
    }

    @Test
    void updateEncounterPathwayProtocol_returnsOk() {
        EncounterController controller = controller();
        ResponseEntity<Map<String, Object>> response = controller.updateEncounterPathwayProtocol(
                "1",
                "req-6",
                "corr-6",
                new EncounterController.UpdateEncounterPathwayProtocolRequest("PATH-SEPSIS-01", "PROTO-CRIT-01"));
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("PATH-SEPSIS-01", data.get("pathwayRef").asText());
        assertEquals("PROTO-CRIT-01", data.get("protocolRef").asText());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubPctClient extends PctServiceClient {
        Map<String, Object> lastDischargeBody = new LinkedHashMap<>();

        StubPctClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode getPatientTimeline(String cpid) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "enc-1").put("type", "CONSULTATION"));
            return arr;
        }

        @Override public JsonNode startEncounter(String journeyId,
                                                 String encounterType,
                                                 String encounterContext,
                                                 String entryPoint,
                                                 String modality,
                                                 String virtualMode,
                                                 String careSetting,
                                                 String priority,
                                                 String triageCategory,
                                                 String pathwayRef,
                                                 String protocolRef) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", 1);
            node.put("encounterType", encounterType);
            node.put("journeyId", journeyId);
            node.put("encounterContext", encounterContext);
            node.put("entryPoint", entryPoint);
            node.put("modality", modality);
            node.put("careSetting", careSetting);
            return node;
        }

        @Override public JsonNode completeEncounter(Long encounterId) {
            return mapper.createObjectNode().put("id", encounterId).put("status", "COMPLETED");
        }

        @Override public JsonNode getEncounter(long encounterId) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", encounterId);
            node.put("journeyId", "journey-1");
            return node;
        }

        @Override public JsonNode startDischarge(String journeyId, String dischargeType) {
            return startDischarge(journeyId, Map.of("dischargeType", dischargeType));
        }

        @Override public JsonNode startDischarge(String journeyId, Map<String, Object> body) {
            lastDischargeBody = new LinkedHashMap<>(body);
            ObjectNode node = mapper.createObjectNode();
            node.put("journeyId", journeyId);
            node.put("status", "INITIATED");
            Object dischargeType = body.get("dischargeType");
            if (dischargeType != null) {
                node.put("dischargeType", String.valueOf(dischargeType));
            }
            return node;
        }

        @Override public JsonNode updateEncounterPathwayProtocol(Long encounterId, String pathwayRef, String protocolRef) {
            return mapper.createObjectNode()
                    .put("id", encounterId)
                    .put("pathwayRef", pathwayRef)
                    .put("protocolRef", protocolRef);
        }
    }

    private static final class StubClinicalClient extends ClinicalKnowledgePlatformClient {
        StubClinicalClient() {
            super(new RestTemplate(), new zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties("http://localhost:8270"));
        }
    }

    private static final class StubCostaClient extends CostaServiceClient {
        int draftCalls = 0;
        String lastDraftEncounterId;
        String lastDraftBillType;
        boolean failDraft = false;
        boolean failBillsByEncounter = false;
        ArrayNode billsByEncounter = mapper.createArrayNode();
        Map<String, ArrayNode> paymentsByBill = new LinkedHashMap<>();

        StubCostaClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode createBillDraft(String encounterId, String billType) {
            if (failDraft) {
                throw new RuntimeException("costa unavailable");
            }
            draftCalls++;
            lastDraftEncounterId = encounterId;
            lastDraftBillType = billType;
            // COSTA-side idempotency: same encounter → same bill id.
            return mapper.createObjectNode().put("billId", "BILL-" + encounterId);
        }

        @Override
        public JsonNode getBillsByEncounter(String encounterId) {
            if (failBillsByEncounter) {
                throw new RuntimeException("costa unavailable");
            }
            return billsByEncounter;
        }

        @Override
        public JsonNode getBillPayments(String billId) {
            return paymentsByBill.getOrDefault(billId, mapper.createArrayNode());
        }
    }

}
