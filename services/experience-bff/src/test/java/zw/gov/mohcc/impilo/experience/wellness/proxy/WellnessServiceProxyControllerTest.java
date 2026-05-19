package zw.gov.mohcc.impilo.experience.wellness.proxy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WellnessServiceProxyControllerTest {

    @Test
    void forward_incrementsLegacyWalletCounterForWalletProxyPath() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        WellnessServiceProxyController controller =
                new WellnessServiceProxyController(restTemplate, meterRegistry, "http://wellness.local");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/mobile/citizen/wallet");
        when(restTemplate.exchange(any(java.net.URI.class), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>("{}".getBytes(), new HttpHeaders(), HttpStatus.OK));

        ResponseEntity<byte[]> response = controller.forward(request, null);

        assertEquals(200, response.getStatusCode().value());
        double count = meterRegistry.get("impilo.legacy.route.requests")
                .tag("route_family", "mobile_citizen_wallet")
                .counter()
                .count();
        assertEquals(1.0, count);
    }
}
