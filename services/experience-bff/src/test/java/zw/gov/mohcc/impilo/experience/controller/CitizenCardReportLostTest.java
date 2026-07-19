package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.MusheWalletServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CitizenCardController.reportLost (CJ8)")
class CitizenCardReportLostTest {

    private static final String REQ = "req-1";
    private static final String CORR = "corr-1";
    private static final String ACTOR = "11111111-1111-1111-1111-111111111111"; // actorId == healthId

    private final ObjectMapper mapper = new ObjectMapper();
    private VitoServiceClient vito;
    private CitizenCardController controller;

    @BeforeEach
    void setUp() {
        vito = mock(VitoServiceClient.class);
        controller = new CitizenCardController(mock(MusheWalletServiceClient.class), vito, mapper);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map<String, Object>> r) {
        return (Map<String, Object>) r.getBody().get("data");
    }

    @Test
    @DisplayName("resolves the caller's active card server-side and revokes it as LOST")
    void reportsLost() throws Exception {
        when(vito.getActiveCard(ACTOR)).thenReturn(mapper.readTree("{\"id\":77,\"status\":\"ACTIVE\"}"));

        ResponseEntity<Map<String, Object>> r = controller.reportLost(
                Map.of("reason", "LOST"), REQ, CORR, ACTOR);

        assertThat(data(r).get("status")).isEqualTo("REPORTED");
        assertThat(data(r).get("revokedCardId")).isEqualTo(77L);
        verify(vito).revokeCard(77L, "LOST");
        verify(vito, never()).requestCard(any(), any());
    }

    @Test
    @DisplayName("STOLEN + requestReplacement revokes then requests a new card")
    void reportsStolenWithReplacement() throws Exception {
        when(vito.getActiveCard(ACTOR)).thenReturn(mapper.readTree("{\"id\":88}"));
        when(vito.requestCard(ACTOR, "PHYSICAL")).thenReturn(mapper.readTree("{\"id\":99,\"status\":\"REQUESTED\"}"));

        ResponseEntity<Map<String, Object>> r = controller.reportLost(
                Map.of("reason", "STOLEN", "requestReplacement", true), REQ, CORR, ACTOR);

        assertThat(data(r).get("reason")).isEqualTo("STOLEN");
        assertThat(data(r).get("replacementRequested")).isEqualTo(true);
        verify(vito).revokeCard(88L, "STOLEN");
        verify(vito).requestCard(ACTOR, "PHYSICAL");
    }

    @Test
    @DisplayName("no active card → NO_ACTIVE_CARD, never revokes")
    void noActiveCard() {
        when(vito.getActiveCard(ACTOR)).thenReturn(null);
        ResponseEntity<Map<String, Object>> r = controller.reportLost(null, REQ, CORR, ACTOR);
        assertThat(data(r).get("status")).isEqualTo("NO_ACTIVE_CARD");
        verify(vito, never()).revokeCard(anyLong(), any());
    }

    @Test
    @DisplayName("missing actor is rejected before any upstream call")
    void missingActorRejected() {
        ResponseEntity<Map<String, Object>> r = controller.reportLost(Map.of(), REQ, CORR, "  ");
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(vito);
    }

    @Test
    @DisplayName("an unknown reason defaults to LOST (only LOST/STOLEN are citizen-reportable)")
    void unknownReasonDefaultsToLost() throws Exception {
        when(vito.getActiveCard(ACTOR)).thenReturn(mapper.readTree("{\"id\":5}"));
        controller.reportLost(Map.of("reason", "DAMAGED"), REQ, CORR, ACTOR);
        verify(vito).revokeCard(5L, "LOST");
    }
}
