package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreTransactionCompositionServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deriveFailureModes_includesSyncPaymentAndTrustFailures() {
        List<String> failures = CoreTransactionCompositionService.deriveFailureModes(
                "PRE_SERVICE_PAYMENT_FAILED",
                "SYNC_FAILED",
                "DENY",
                "DENIED"
        );
        assertTrue(failures.contains("PRE_SERVICE_PAYMENT_FAILED"));
        assertTrue(failures.contains("PAYMENT_FAILED"));
        assertTrue(failures.contains("SYNC_FAILED"));
        assertTrue(failures.contains("ACCESS_DENIED"));
        assertTrue(failures.contains("CONSENT_DENIED"));
    }

    @Test
    void nompiloModeForState_mapsOperationalAndHandoffStates() {
        assertEquals("OPERATIONS_INSIGHT", CoreTransactionCompositionService.nompiloModeForState("PENDING_SYNC"));
        assertEquals("STEP_BY_STEP_GUIDE", CoreTransactionCompositionService.nompiloModeForState("PRE_SERVICE_PAYMENT_REQUIRED"));
        assertEquals("HUMAN_HANDOFF", CoreTransactionCompositionService.nompiloModeForState("ACCESS_DENIED"));
        assertEquals("CONTEXTUAL_COMPANION", CoreTransactionCompositionService.nompiloModeForState("IN_SERVICE"));
    }

    @Test
    void feedbackAndHandoffMethods_returnAcceptedResponses() {
        CoreTransactionCompositionService service = new CoreTransactionCompositionService(null, null, null, null, null, MAPPER);
        ObjectNode feedback = service.submitFeedback("tx-1", Map.of("channel", "IN_APP"));
        ObjectNode handoff = service.requestNompiloHandoff("tx-1", Map.of("handoffOptionId", "helpdesk"));
        ObjectNode command = service.executeNompiloCommand("tx-1", Map.of("query", "find service"));
        assertEquals("ACCEPTED", feedback.get("status").asText());
        assertEquals("ACCEPTED", handoff.get("status").asText());
        assertEquals("ACCEPTED", command.get("status").asText());
        assertEquals("FIND_SERVICE", command.get("intent").asText());
    }

    @Test
    void deriveBlockerReasons_includesWorkflowHintsAndStateFallbacks() {
        ObjectNode workflow = MAPPER.createObjectNode();
        workflow.put("paymentFailureReason", "Wallet timeout");
        workflow.put("syncFailureReason", "Network unavailable");
        List<String> reasons = CoreTransactionCompositionService.deriveBlockerReasons(
                "PRE_SERVICE_PAYMENT_REQUIRED",
                workflow
        );
        assertTrue(reasons.contains("Wallet timeout"));
        assertTrue(reasons.contains("Network unavailable"));
        assertTrue(reasons.contains("Pre-service payment or exemption confirmation is required."));
    }

    @Test
    void classifyNompiloIntent_mapsExpectedQueries() {
        assertEquals("FIND_PROVIDER", CoreTransactionCompositionService.classifyNompiloIntent("find provider on duty"));
        assertEquals("REQUEST_DASHBOARD", CoreTransactionCompositionService.classifyNompiloIntent("open dashboard for bottlenecks"));
        assertEquals("ASK_WORKFLOW_HELP", CoreTransactionCompositionService.classifyNompiloIntent("what is next"));
    }
}
