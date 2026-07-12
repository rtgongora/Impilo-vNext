package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PublicGatewayPractitionerBffControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void verify_returnsDownstreamDisclosure() {
        StubClient stub = new StubClient();
        PublicGatewayPractitionerBffController controller = new PublicGatewayPractitionerBffController(stub);
        ResponseEntity<JsonNode> response = controller.verify("MDPCZ-778899");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("REGISTERED", response.getBody().get("registerStatus").asText());
        assertEquals("MDPCZ-778899", stub.lastNumber);
    }

    @Test
    void verify_trimsTheNumberBeforeCallingDownstream() {
        StubClient stub = new StubClient();
        PublicGatewayPractitionerBffController controller = new PublicGatewayPractitionerBffController(stub);
        controller.verify("  MDPCZ-778899  ");
        assertEquals("MDPCZ-778899", stub.lastNumber);
    }

    @Test
    void verify_missIsStill200_withNotFoundStatusInBody() {
        StubClient stub = new StubClient();
        stub.notFoundStatus = true;
        PublicGatewayPractitionerBffController controller = new PublicGatewayPractitionerBffController(stub);
        ResponseEntity<JsonNode> response = controller.verify("NOPE-1");
        assertEquals(200, response.getStatusCode().value());
        assertEquals("NOT_FOUND", response.getBody().get("registerStatus").asText());
    }

    @Test
    void verify_blankNumberMapsTo400_withoutCallingDownstream() {
        StubClient stub = new StubClient();
        PublicGatewayPractitionerBffController controller = new PublicGatewayPractitionerBffController(stub);
        assertEquals(400, controller.verify("   ").getStatusCode().value());
        assertEquals(null, stub.lastNumber);
    }

    @Test
    void verify_downstreamFailureMapsTo502() {
        StubClient stub = new StubClient();
        stub.explode = true;
        PublicGatewayPractitionerBffController controller = new PublicGatewayPractitionerBffController(stub);
        assertEquals(502, controller.verify("MDPCZ-778899").getStatusCode().value());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubClient extends VarapiServiceClient {
        String lastNumber;
        boolean notFoundStatus;
        boolean explode;

        StubClient() { super(new RestTemplate(), endpoints()); }

        @Override public JsonNode publicPractitionerVerify(String registrationNumber) {
            if (explode) throw new RuntimeException("downstream unavailable");
            lastNumber = registrationNumber;
            ObjectNode node = mapper.createObjectNode();
            node.put("registerStatus", notFoundStatus ? "NOT_FOUND" : "REGISTERED");
            return node;
        }
    }
}
