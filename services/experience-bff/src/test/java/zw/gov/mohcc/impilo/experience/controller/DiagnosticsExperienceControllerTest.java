package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.client.PacsServiceClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DiagnosticsExperienceController} — proxies OROS diagnostics endpoints and
 * degrades to 502 on upstream failure (never fake-success).
 */
class DiagnosticsExperienceControllerTest {

    private final OrosServiceClient orosClient = mock(OrosServiceClient.class);
    private final PacsServiceClient pacsClient = mock(PacsServiceClient.class);
    private final DiagnosticsExperienceController controller = new DiagnosticsExperienceController(orosClient, pacsClient);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void orders_wrapsUpstreamDataInEnvelope() {
        ArrayNode upstream = objectMapper.createArrayNode();
        upstream.add(objectMapper.createObjectNode().put("orderId", "ORD-1"));
        when(orosClient.listOrders("CPID-1", null, "PLACED", "IMAGING")).thenReturn(upstream);

        ResponseEntity<Map<String, Object>> resp =
                controller.orders("req-1", "cor-1", "CPID-1", null, "PLACED", "IMAGING");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("data").containsKey("meta");
        assertThat(resp.getBody().get("data")).isEqualTo(upstream);
        verify(orosClient).listOrders("CPID-1", null, "PLACED", "IMAGING");
    }

    @Test
    void resultObservations_proxiesUpstream() {
        ArrayNode upstream = objectMapper.createArrayNode();
        upstream.add(objectMapper.createObjectNode().put("analyteName", "Haemoglobin").put("unit", "g/dL"));
        when(orosClient.resultObservations("RES-1")).thenReturn(upstream);

        ResponseEntity<Map<String, Object>> resp = controller.resultObservations("req-1", "cor-1", "RES-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("data")).isEqualTo(upstream);
        verify(orosClient).resultObservations("RES-1");
    }

    @Test
    void orderSpecimens_proxiesUpstream() {
        ArrayNode upstream = objectMapper.createArrayNode();
        upstream.add(objectMapper.createObjectNode().put("sampleId", "SPC-1").put("status", "RECEIVED"));
        when(orosClient.orderSpecimens("ORD-1")).thenReturn(upstream);

        ResponseEntity<Map<String, Object>> resp = controller.orderSpecimens("req-1", "cor-1", "ORD-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("data")).isEqualTo(upstream);
        verify(orosClient).orderSpecimens("ORD-1");
    }

    @Test
    void resultsInbox_degradesTo502OnUpstreamFailure() {
        when(orosClient.resultsInbox(any())).thenThrow(new RuntimeException("connection refused"));

        ResponseEntity<Map<String, Object>> resp = controller.resultsInbox("req-1", "cor-1", "dr-9");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(resp.getBody()).containsKey("error");
    }

    @Test
    void createDraft_proxiesBodyAndWrapsResult() {
        when(orosClient.createDraft(anyMap()))
                .thenReturn(objectMapper.createObjectNode().put("orderId", "ORD-NEW").put("status", "DRAFT"));

        ResponseEntity<Map<String, Object>> resp =
                controller.createDraft("req-1", "cor-1", Map.of("orderType", "IMAGING", "patientCpid", "CPID-1"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("data")).isNotNull();
        verify(orosClient).createDraft(anyMap());
    }

    @Test
    void launchViewer_resolvesStudyAndLaunchesSession() {
        when(orosClient.getOrder("ORD-1"))
                .thenReturn(objectMapper.createObjectNode().put("studyUid", "1.2.3"));
        ArrayNode studies = objectMapper.createArrayNode();
        studies.add(objectMapper.createObjectNode().put("id", "study-77"));
        when(pacsClient.searchStudies(anyMap())).thenReturn(studies);
        when(pacsClient.launchViewerSession(eq("study-77"), anyMap()))
                .thenReturn(objectMapper.createObjectNode().put("viewerUrl", "https://viewer/launch"));

        ResponseEntity<Map<String, Object>> resp = controller.launchViewer("req-1", "cor-1", "ORD-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("data")).isNotNull();
        verify(pacsClient).launchViewerSession(eq("study-77"), anyMap());
    }

    @Test
    void launchViewer_conflictWhenNoLinkedStudy() {
        when(orosClient.getOrder("ORD-2")).thenReturn(objectMapper.createObjectNode());

        ResponseEntity<Map<String, Object>> resp = controller.launchViewer("req-1", "cor-1", "ORD-2");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(pacsClient, never()).launchViewerSession(any(), anyMap());
    }

    @Test
    void reconcileSummary_proxiesUpstream() {
        when(orosClient.reconcileDiagnosticsSummary())
                .thenReturn(objectMapper.createObjectNode().put("RECEIVED_NOT_ACCEPTED", 2));

        ResponseEntity<Map<String, Object>> resp = controller.reconcileSummary("req-1", "cor-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("data")).isNotNull();
    }
}
