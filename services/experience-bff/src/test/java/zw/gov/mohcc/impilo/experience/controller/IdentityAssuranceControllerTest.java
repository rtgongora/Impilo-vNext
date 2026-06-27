package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Proves the assurance BFF proxies identity-assurance-service (no longer fabricates) + wraps the envelope. */
@ExtendWith(MockitoExtension.class)
class IdentityAssuranceControllerTest {

    @Mock private IdentityAssuranceServiceClient assuranceClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    @Test
    void status_proxiesService_andWrapsEnvelope() {
        ObjectNode status = mapper.createObjectNode().put("currentLevel", "LOA1");
        when(assuranceClient.getAssuranceStatus()).thenReturn(status);

        IdentityAssuranceController controller = new IdentityAssuranceController(assuranceClient);
        ResponseEntity<Map<String, Object>> response = controller.getAssuranceStatus("req-1", "corr-1");

        assertThat(response.getBody().get("data")).isEqualTo(status);
        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        assertThat(meta.get("request_id")).isEqualTo("req-1");
        // The real per-actor level is surfaced — not a hardcoded LOA2.
        assertThat(((ObjectNode) response.getBody().get("data")).get("currentLevel").asText()).isEqualTo("LOA1");
    }

    @Test
    void upgradeRequest_forwardsBody_and201() {
        Map<String, Object> body = Map.of("targetLevel", "LOA3", "method", "IN_PERSON_VERIFICATION");
        ObjectNode created = mapper.createObjectNode().put("status", "PENDING");
        when(assuranceClient.requestUpgrade(eq(body))).thenReturn(created);

        IdentityAssuranceController controller = new IdentityAssuranceController(assuranceClient);
        ResponseEntity<Map<String, Object>> response = controller.requestUpgrade("req-2", "corr-2", body);

        verify(assuranceClient).requestUpgrade(body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("data")).isEqualTo(created);
    }
}
