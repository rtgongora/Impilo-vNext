package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TriageImagingLinkControllerTest {

    private PctServiceClient pct;
    private TshepoAuditServiceClient audit;
    private TriageImagingLinkController controller;
    private HttpServletRequest request;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        pct = Mockito.mock(PctServiceClient.class);
        audit = Mockito.mock(TshepoAuditServiceClient.class);
        controller = new TriageImagingLinkController(pct, audit);
        request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader(CompanionHeaders.TENANT_ID)).thenReturn("00000000-0000-0000-0000-000000000001");
        Mockito.when(request.getHeader(CompanionHeaders.CORRELATION_ID)).thenReturn("00000000-0000-0000-0000-000000000002");
        Mockito.when(request.getHeader(CompanionHeaders.ACTOR_ID)).thenReturn("actor-1");
        Mockito.when(request.getHeader(CompanionHeaders.ACTOR_TYPE)).thenReturn("PROVIDER");
        Mockito.when(request.getHeader(CompanionHeaders.PURPOSE_OF_USE)).thenReturn("TREATMENT");
    }

    @Test
    void createLink_proxiesToPctAndAudits() {
        ObjectNode created = mapper.createObjectNode().put("governedStudyId", "42");
        Mockito.when(pct.createEncounterImagingLink(eq(10L), any())).thenReturn(created);

        ResponseEntity<Map<String, Object>> response = controller.createLink(
                request, 10L, Map.of("governed_study_id", "42", "oros_order_id", "OROS-1"));

        assertEquals(200, response.getStatusCode().value());
        Mockito.verify(pct).createEncounterImagingLink(eq(10L), eq(Map.of(
                "governedStudyId", "42",
                "orosOrderId", "OROS-1")));
        Mockito.verify(audit).ingestAuditEvent(any());
    }

    @Test
    void listLinks_proxiesToPctAndAudits() {
        Mockito.when(pct.listEncounterImagingLinks(10L)).thenReturn(mapper.createArrayNode());

        ResponseEntity<Map<String, Object>> response = controller.listLinks(request, 10L);

        assertEquals(200, response.getStatusCode().value());
        Mockito.verify(pct).listEncounterImagingLinks(10L);
        Mockito.verify(audit).ingestAuditEvent(any());
    }
}
