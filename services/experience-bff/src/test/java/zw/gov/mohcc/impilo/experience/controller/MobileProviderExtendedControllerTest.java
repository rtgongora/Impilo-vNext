package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.PharmacyServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MobileProviderExtendedControllerTest {

    @Test
    void billingChargesReturnsNotImplementedUntilWired() {
        MobileProviderExtendedController controller = new MobileProviderExtendedController(
                new StubPctClient(), new StubVitoClient(), new StubPharmacyClient(), new StubCostaClient(), new StubOrosClient());

        ResponseEntity<Map<String, Object>> response = controller.getCharges("tenant-1", "enc-1");
        assertEquals(501, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("BILLING_ROUTE_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void billingChargeCaptureReturnsNotImplementedUntilWired() {
        MobileProviderExtendedController controller = new MobileProviderExtendedController(
                new StubPctClient(), new StubVitoClient(), new StubPharmacyClient(), new StubCostaClient(), new StubOrosClient());

        ResponseEntity<Map<String, Object>> response = controller.captureCharge("tenant-1", Map.of("encounterId", "enc-1"));
        assertEquals(501, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("BILLING_ROUTE_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubPctClient extends PctServiceClient {
        StubPctClient() { super(new RestTemplate(), endpoints(), new ObjectMapper()); }
    }

    private static final class StubVitoClient extends VitoServiceClient {
        StubVitoClient() { super(new RestTemplate(), endpoints()); }
    }

    private static final class StubPharmacyClient extends PharmacyServiceClient {
        StubPharmacyClient() { super(new RestTemplate(), endpoints()); }
    }

    private static final class StubCostaClient extends CostaServiceClient {
        StubCostaClient() { super(new RestTemplate(), endpoints()); }
    }

    private static final class StubOrosClient extends OrosServiceClient {
        StubOrosClient() { super(new RestTemplate(), endpoints()); }
    }
}
