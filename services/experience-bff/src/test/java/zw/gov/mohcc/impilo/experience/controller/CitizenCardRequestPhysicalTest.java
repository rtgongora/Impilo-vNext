package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
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

@DisplayName("CitizenCardController.requestPhysicalCard (B3 — fresh card through PROOFING)")
class CitizenCardRequestPhysicalTest {

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

    private JsonNode data(ResponseEntity<Map<String, Object>> r) {
        return (JsonNode) r.getBody().get("data");
    }

    @Test
    @DisplayName("submits a NEW request on the PORTAL channel (auto-PROOFING)")
    void submitsFreshThroughProofing() throws Exception {
        when(vito.submitPortalIssuance(ACTOR, "NEW"))
                .thenReturn(mapper.readTree("{\"id\":42,\"state\":\"PROOFING\"}"));

        ResponseEntity<Map<String, Object>> r = controller.requestPhysicalCard(null, REQ, CORR, ACTOR);

        assertThat(data(r).get("state").asText()).isEqualTo("PROOFING");
        verify(vito).submitPortalIssuance(ACTOR, "NEW");
    }

    @Test
    @DisplayName("explicit REPLACEMENT type is honored")
    void replacementType() throws Exception {
        when(vito.submitPortalIssuance(ACTOR, "REPLACEMENT"))
                .thenReturn(mapper.readTree("{\"id\":43,\"state\":\"PROOFING\"}"));

        controller.requestPhysicalCard(Map.of("type", "replacement"), REQ, CORR, ACTOR);

        verify(vito).submitPortalIssuance(ACTOR, "REPLACEMENT");
    }

    @Test
    @DisplayName("missing actor rejected before any upstream call")
    void missingActor() {
        ResponseEntity<Map<String, Object>> r = controller.requestPhysicalCard(Map.of(), REQ, CORR, "  ");
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(vito);
    }

    @Test
    @DisplayName("issuance status proxies vito by request id")
    void issuanceStatus() throws Exception {
        when(vito.getIssuanceRequest(42L))
                .thenReturn(mapper.readTree("{\"id\":42,\"state\":\"APPROVED\"}"));

        ResponseEntity<Map<String, Object>> r = controller.issuanceStatus(42L, REQ, CORR);

        assertThat(data(r).get("state").asText()).isEqualTo("APPROVED");
    }
}
