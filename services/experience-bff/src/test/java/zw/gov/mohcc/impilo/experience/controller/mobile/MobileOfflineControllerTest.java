package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.TshepoOfflineServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MobileOfflineControllerTest {

    @Test
    void verifyEntitlement_returnsAcceptedEnvelope() {
        MobileOfflineController controller =
                new MobileOfflineController(new ObjectMapper(), new StubOfflineClient());
        ResponseEntity<Map<String, Object>> response = controller.verifyEntitlement(
                "tenant-1",
                "req-1",
                "corr-1",
                new MobileOfflineController.VerifyEntitlementRequest("cpid-1", "fac-1", "CONSULT"));
        assertEquals(200, response.getStatusCode().value());
    }

    private static final class StubOfflineClient extends TshepoOfflineServiceClient {
        StubOfflineClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }
    }
}
