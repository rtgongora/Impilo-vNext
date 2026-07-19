package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.ButanoServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.service.PiiAccessAuditService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("EmergencyCardPhrController — governed break-glass card PHR read (B5)")
class EmergencyCardPhrControllerTest {

    private static final String TENANT = "11111111-1111-1111-1111-111111111111";
    private static final String RESPONDER = "22222222-2222-2222-2222-222222222222";
    private static final String REQ = "req-1";
    private static final String CORR = "corr-1";

    private final ObjectMapper mapper = new ObjectMapper();
    private VitoServiceClient vito;
    private TshepoAuthzServiceClient authz;
    private ButanoServiceClient butano;
    private PiiAccessAuditService audit;
    private EmergencyCardPhrController controller;

    @BeforeEach
    void setUp() {
        vito = mock(VitoServiceClient.class);
        authz = mock(TshepoAuthzServiceClient.class);
        butano = mock(ButanoServiceClient.class);
        audit = mock(PiiAccessAuditService.class);
        controller = new EmergencyCardPhrController(vito, authz, butano, audit, mapper);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map<String, Object>> r) {
        return (Map<String, Object>) r.getBody().get("data");
    }

    private ResponseEntity<Map<String, Object>> call(Map<String, Object> body) {
        return controller.breakGlassCardPhr(body, TENANT, RESPONDER, REQ, CORR, "PROVIDER", null);
    }

    @Test
    @DisplayName("card resolves → break-glass opened → subject summary read + audited")
    void happyPath() throws Exception {
        when(vito.resolveCard("CARD-1", null, null))
                .thenReturn(mapper.readTree("{\"healthId\":\"cpid-9\",\"cpid\":\"cpid-9\",\"cardStatus\":\"ACTIVE\"}"));
        when(authz.requestBreakGlass(anyMap()))
                .thenReturn(mapper.readTree("{\"id\":\"bg-42\"}"));
        when(butano.getIpsSummary("cpid-9"))
                .thenReturn(ResponseEntity.ok("{\"resourceType\":\"Bundle\"}"));

        ResponseEntity<Map<String, Object>> r = call(Map.of("cardNumber", "CARD-1", "reason", "unconscious patient"));

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(data(r).get("subjectCpid")).isEqualTo("cpid-9");
        assertThat(data(r).get("breakGlassRequestId")).isEqualTo("bg-42");
        // Read is the RESOLVED subject's, not the responder's own.
        verify(butano).getIpsSummary("cpid-9");
        verify(audit).recordAccess(eq(TENANT), eq(RESPONDER), eq("PROVIDER"), eq("cpid-9"),
                eq("PATIENT"), eq("VIEW"), any(), eq("BREAK_GLASS"), any(), any(), any(), any(), isNull());
    }

    @Test
    @DisplayName("missing reason → 400, nothing touched")
    void missingReason() {
        ResponseEntity<Map<String, Object>> r = call(Map.of("cardNumber", "CARD-1"));
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(vito, authz, butano);
    }

    @Test
    @DisplayName("no card presentation → 400")
    void missingCard() {
        ResponseEntity<Map<String, Object>> r = call(Map.of("reason", "emergency"));
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(vito, authz, butano);
    }

    @Test
    @DisplayName("unresolvable card → 422, never opens break-glass or reads")
    void unresolvedCard() {
        when(vito.resolveCard(any(), any(), any())).thenReturn(null);
        ResponseEntity<Map<String, Object>> r = call(Map.of("cardNumber", "NOPE", "reason", "emergency"));
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        verifyNoInteractions(authz, butano);
    }

    @Test
    @DisplayName("break-glass request fails → 502 and PHR is NOT read (read gated on governed request)")
    void breakGlassFailsGatesRead() throws Exception {
        when(vito.resolveCard(any(), any(), any()))
                .thenReturn(mapper.readTree("{\"cpid\":\"cpid-9\"}"));
        when(authz.requestBreakGlass(anyMap())).thenThrow(new RuntimeException("authz down"));

        ResponseEntity<Map<String, Object>> r = call(Map.of("cardNumber", "CARD-1", "reason", "emergency"));

        assertThat(r.getStatusCode().value()).isEqualTo(502);
        verifyNoInteractions(butano);
        verify(audit, never()).recordAccess(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
