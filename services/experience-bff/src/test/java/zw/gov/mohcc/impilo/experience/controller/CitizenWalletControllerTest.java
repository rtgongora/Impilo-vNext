package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.service.PiiAccessAuditService;
import zw.gov.mohcc.impilo.experience.service.WalletOverviewService;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the wallet BFF endpoint resolves the person from {@code X-Actor-ID} (or the delegated
 * {@code X-Subject-ID}), wraps the result in the data/meta envelope, audits the sensitive access,
 * and routes corrections to VITO's workflow rather than mutating identity inline.
 */
@ExtendWith(MockitoExtension.class)
class CitizenWalletControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock private WalletOverviewService walletService;
    @Mock private VitoServiceClient vitoClient;
    @Mock private PiiAccessAuditService auditService;

    private CitizenWalletController controller() {
        return new CitizenWalletController(walletService, vitoClient, auditService);
    }

    @Test
    void selfViewComposesForActorAndAuditsAsSelfService() {
        Map<String, Object> built = new LinkedHashMap<>();
        built.put("identity", Map.of("healthId", "CPID-1"));
        when(walletService.buildOverview("CPID-1")).thenReturn(built);

        ResponseEntity<Map<String, Object>> res = controller().overview(
                "tenant", "req", "corr", "CPID-1", null, "CITIZEN", null, null, new MockHttpServletRequest());

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody().get("data")).isEqualTo(built);
        assertThat(((Map<?, ?>) res.getBody().get("data")).get("viewingAs")).isEqualTo("SELF");
        verify(walletService).buildOverview("CPID-1");
        verify(auditService).recordAccess(eq("tenant"), eq("CPID-1"), eq("CITIZEN"), eq("CPID-1"),
                eq("PATIENT"), eq("VIEW"), any(), eq("SELF_SERVICE"), anyString(), eq("FULL"),
                any(), any(), any());
    }

    @Test
    void delegatedViewComposesForSubjectAndAuditsCrossPersonAccess() {
        Map<String, Object> built = new LinkedHashMap<>();
        when(walletService.buildOverview("CHILD-1")).thenReturn(built);

        ResponseEntity<Map<String, Object>> res = controller().overview(
                "tenant", "req", "corr", "GUARDIAN-1", "Patient/CHILD-1", "CITIZEN",
                "TREATMENT", "FAC-9", new MockHttpServletRequest());

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map<?, ?>) res.getBody().get("data")).get("viewingAs")).isEqualTo("DELEGATE");
        assertThat(((Map<?, ?>) res.getBody().get("data")).get("subjectId")).isEqualTo("CHILD-1");
        verify(walletService).buildOverview("CHILD-1");
        // Cross-person access is audited with the delegate as actor and the dependant as subject.
        verify(auditService).recordAccess(eq("tenant"), eq("GUARDIAN-1"), eq("CITIZEN"), eq("CHILD-1"),
                eq("PATIENT"), eq("VIEW"), any(), eq("TREATMENT"), anyString(), eq("FULL"),
                any(), any(), eq("FAC-9"));
    }

    @Test
    void correctionRoutesToVitoWorkflowAndAudits() {
        when(vitoClient.recordClientCorrection(eq("CPID-1"), any()))
                .thenReturn(mapper.createObjectNode().put("status", "PENDING_REVIEW"));
        Map<String, Object> body = Map.of("phone", "+263770000001");

        ResponseEntity<Map<String, Object>> res = controller().submitCorrection(
                "tenant", "req", "corr", "CPID-1", "CITIZEN", null, body, new MockHttpServletRequest());

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        verify(vitoClient).recordClientCorrection("CPID-1", body);

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordAccess(eq("tenant"), eq("CPID-1"), eq("CITIZEN"), eq("CPID-1"),
                eq("PATIENT"), action.capture(), any(), eq("SELF_SERVICE"), anyString(), eq("FULL"),
                any(), any(), any());
        assertThat(action.getValue()).isEqualTo("CORRECTION_REQUEST");
    }
}
