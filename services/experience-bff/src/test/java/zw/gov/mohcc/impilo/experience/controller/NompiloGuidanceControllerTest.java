package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;
import zw.gov.mohcc.impilo.experience.client.LearningServiceClient;
import zw.gov.mohcc.impilo.experience.service.NompiloSignalService;
import zw.gov.mohcc.impilo.experience.service.NompiloSignalService.CitizenSignals;
import zw.gov.mohcc.impilo.experience.service.PiiAccessAuditService;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NompiloGuidanceControllerTest {

    @Mock private GuidanceServiceClient guidanceClient;
    @Mock private NompiloSignalService signalService;
    @Mock private LearningServiceClient learningClient;
    @Mock private PiiAccessAuditService auditService;

    private final ObjectMapper mapper = new ObjectMapper();

    private NompiloGuidanceController controller() {
        return new NompiloGuidanceController(guidanceClient, signalService, learningClient, auditService);
    }

    private JsonNode guidanceWith(boolean auditRequired) {
        ObjectNode root = mapper.createObjectNode();
        var arr = root.putArray("guidance");
        ObjectNode item = arr.addObject();
        item.put("key", "UPGRADE_TRUST");
        item.put("auditRequired", auditRequired);
        return root;
    }

    @Test
    @SuppressWarnings("unchecked")
    void context_derives_citizen_signals_and_forwards_them() {
        when(signalService.citizenSignals(eq("CPID-1"), anyBoolean()))
                .thenReturn(new CitizenSignals(Set.of("trust.not_verified"), 1));
        when(guidanceClient.resolveContext(any())).thenReturn(guidanceWith(false));

        var resp = controller().context("t1", "req", "corr", "CPID-1", "CITIZEN", null, null,
                Map.of("routePath", "/citizen/wallet"), null);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(guidanceClient).resolveContext(cap.capture());
        Map<String, Object> req = cap.getValue();
        assertThat(req.get("userType")).isEqualTo("CITIZEN");
        assertThat(((java.util.List<String>) req.get("signals"))).contains("trust.not_verified");
        // No cross-person, no sensitive item → no audit emitted.
        verifyNoInteractions(auditService);
    }

    @Test
    void context_audits_when_a_sensitive_item_is_returned() {
        when(signalService.citizenSignals(anyString(), anyBoolean()))
                .thenReturn(new CitizenSignals(Set.of("trust.not_verified"), 1));
        when(guidanceClient.resolveContext(any())).thenReturn(guidanceWith(true));

        controller().context("t1", "req", "corr", "CPID-1", "CITIZEN", null, null,
                Map.of("routePath", "/citizen/wallet"), null);

        verify(auditService).recordAccess(eq("t1"), eq("CPID-1"), anyString(), eq("CPID-1"),
                anyString(), eq("NOMPILO_SENSITIVE_GUIDANCE"), any(), anyString(), anyString(),
                anyString(), any(), any(), any());
    }

    @Test
    void context_audits_cross_person_proxy_access() {
        when(guidanceClient.resolveContext(any())).thenReturn(guidanceWith(false));

        // Delegated: X-Subject-ID differs from X-Actor-ID → proxy guidance, must be audited.
        controller().context("t1", "req", "corr", "GUARDIAN-1", "CAREGIVER", "CHILD-1", null,
                Map.of("routePath", "/citizen/wallet"), null);

        verify(auditService).recordAccess(eq("t1"), eq("GUARDIAN-1"), anyString(), eq("CHILD-1"),
                anyString(), eq("NOMPILO_GUIDANCE_PROXY"), any(), anyString(), anyString(),
                anyString(), any(), any(), any());
        // A delegated citizen does NOT auto-derive self signals (relationship is PROXY).
        verify(signalService, never()).citizenSignals(anyString(), anyBoolean());
    }

    @Test
    void explain_locked_always_audits_the_denied_explanation() {
        ObjectNode explanation = mapper.createObjectNode();
        explanation.put("title", "This action isn't available right now");
        when(guidanceClient.explainLocked(any())).thenReturn(explanation);

        var resp = controller().explainLocked("t1", "req", "corr", "CPID-1", "CITIZEN", null, null,
                Map.of("reasonCode", "REQUIRES_CONSENT", "routePath", "/citizen/wallet/records"), null);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(auditService).recordAccess(eq("t1"), eq("CPID-1"), anyString(), eq("CPID-1"),
                anyString(), eq("NOMPILO_LOCKED_EXPLANATION"), any(), anyString(), anyString(),
                anyString(), any(), any(), any());
    }

    @Test
    void follow_up_routes_through_the_handoff_lifecycle_not_a_direct_notification() {
        when(guidanceClient.requestHandoff(any())).thenReturn(mapper.createObjectNode().put("status", "QUEUED"));

        var resp = controller().requestFollowUp("t1", "req", "corr", "CPID-1", null, null,
                Map.of("reason", "call me back", "guidanceKey", "SETTLE_BILL"), null);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        // Nompilo REQUESTS comms via the handoff lifecycle; it never calls a notification client.
        verify(guidanceClient).requestHandoff(any());
        verify(auditService).recordAccess(eq("t1"), eq("CPID-1"), anyString(), eq("CPID-1"),
                anyString(), eq("NOMPILO_KHULUMA_FOLLOW_UP"), any(), anyString(), anyString(),
                anyString(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void next_actions_defaultContext_derivesCitizenSignalsUnchanged() {
        when(signalService.citizenSignals(eq("CPID-1"), eq(false)))
                .thenReturn(new CitizenSignals(Set.of("trust.not_verified"), 1));
        when(guidanceClient.resolveContext(any())).thenReturn(guidanceWith(false));

        var resp = controller().nextActions("t1", "req", "corr", "CPID-1", "CITIZEN", null, "citizen", null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(guidanceClient).resolveContext(cap.capture());
        Map<String, Object> req = cap.getValue();
        assertThat(req.get("userType")).isEqualTo("CITIZEN");
        assertThat(req.get("routePath")).isEqualTo("/citizen/wallet");
        assertThat((List<String>) req.get("signals")).contains("trust.not_verified");
        verifyNoInteractions(auditService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void next_actions_workContext_derivesWorkSignalsInsteadOfCitizenSignals() {
        when(signalService.workSignals(eq("PROV-1"), any(), eq("ctx-1"), eq("CLINICAL_CARE")))
                .thenReturn(new NompiloSignalService.WorkSignals(Set.of("work.act_now_pending"), null));
        when(guidanceClient.resolveContext(any())).thenReturn(guidanceWith(false));

        var resp = controller().nextActions("t1", "req", "corr", "PROV-1", "PROVIDER", null,
                "work", "ctx-1", "CLINICAL_CARE");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(signalService, never()).citizenSignals(anyString(), anyBoolean());
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(guidanceClient).resolveContext(cap.capture());
        Map<String, Object> req = cap.getValue();
        assertThat(req.get("userType")).isEqualTo("PROVIDER");
        assertThat(req.get("routePath")).isEqualTo("/work");
        assertThat(req.get("trustLoa")).isEqualTo(4);
        assertThat((List<String>) req.get("signals")).contains("work.act_now_pending");
    }

    @Test
    void next_actions_workContext_missingContextId_returnsSafePartialWithoutCallingGuidance() {
        var resp = controller().nextActions("t1", "req", "corr", "PROV-1", "PROVIDER", null, "work", null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map<?, ?>) resp.getBody().get("data")).get("degraded")).isEqualTo(true);
        verifyNoInteractions(guidanceClient);
    }

    @Test
    void downstream_failure_degrades_to_a_safe_partial() {
        when(signalService.citizenSignals(anyString(), anyBoolean()))
                .thenReturn(new CitizenSignals(Set.of(), 1));
        when(guidanceClient.resolveContext(any())).thenThrow(new RuntimeException("guidance down"));

        var resp = controller().context("t1", "req", "corr", "CPID-1", "CITIZEN", null, null,
                Map.of("routePath", "/citizen/wallet"), null);

        // Never fail the page: 200 with an empty, truthfully-degraded guidance set.
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        Object data = resp.getBody().get("data");
        assertThat(((Map<?, ?>) data).get("degraded")).isEqualTo(true);
    }
}
