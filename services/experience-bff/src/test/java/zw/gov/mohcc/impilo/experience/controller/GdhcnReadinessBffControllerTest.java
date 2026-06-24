package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Proves the B6 readiness BFF proxies authz and wraps the standard envelope. */
@ExtendWith(MockitoExtension.class)
class GdhcnReadinessBffControllerTest {

    @Mock private TshepoAuthzServiceClient authzClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    @Test
    void list_proxiesAuthz_andWrapsEnvelope() {
        ArrayNode domains = mapper.createArrayNode();
        domains.addObject().put("domainKey", "GOVERNANCE");
        when(authzClient.listGdhcnReadiness()).thenReturn(domains);

        GdhcnReadinessBffController controller = new GdhcnReadinessBffController(authzClient);
        ResponseEntity<Map<String, Object>> response = controller.list("req-1", "corr-1");

        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("type")).isEqualTo("gdhcn-readiness");
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        assertThat(attributes.get("domains")).isEqualTo(domains);
        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        assertThat(meta.get("request_id")).isEqualTo("req-1");
    }

    @SuppressWarnings("unchecked")
    @Test
    void update_forwardsDomainKeyAndBody() {
        ObjectNode updated = mapper.createObjectNode().put("domainKey", "SIGNATURE_VERIFICATION");
        Map<String, Object> body = Map.of("currentMaturity", "FOUNDATION_IN_PROGRESS");
        when(authzClient.updateGdhcnReadiness(eq("SIGNATURE_VERIFICATION"), eq(body))).thenReturn(updated);

        GdhcnReadinessBffController controller = new GdhcnReadinessBffController(authzClient);
        ResponseEntity<Map<String, Object>> response =
                controller.update("req-2", "corr-2", "SIGNATURE_VERIFICATION", body);

        verify(authzClient).updateGdhcnReadiness("SIGNATURE_VERIFICATION", body);
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        assertThat(attributes.get("domain")).isEqualTo(updated);
    }
}
