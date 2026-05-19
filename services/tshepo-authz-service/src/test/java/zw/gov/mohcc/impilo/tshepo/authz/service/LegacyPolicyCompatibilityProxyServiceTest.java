package zw.gov.mohcc.impilo.tshepo.authz.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LegacyPolicyCompatibilityProxyServiceTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private LegacyPolicyCompatibilityProxyService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new LegacyPolicyCompatibilityProxyService(restTemplate, "http://localhost:8079");
    }

    @Test
    void forwardsLegacyPolicyRequestAndHeaders() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Tenant-Id")).thenReturn("00000000-0000-0000-0000-000000000001");
        when(request.getHeader("X-Correlation-Id")).thenReturn("11111111-1111-1111-1111-111111111111");
        when(request.getHeader("X-Actor-Id")).thenReturn("provider-1");
        when(request.getHeader("X-Actor-Type")).thenReturn("PROVIDER");
        when(request.getHeader("X-Purpose-Of-Use")).thenReturn("TREATMENT");
        when(request.getHeader("Authorization")).thenReturn("Bearer token");

        server.expect(once(), requestTo("http://localhost:8079/v1/biometric-policy/evaluate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Tenant-Id", "00000000-0000-0000-0000-000000000001"))
                .andExpect(header("X-Correlation-Id", "11111111-1111-1111-1111-111111111111"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"policyOutcome\":\"ALLOWED\",\"verificationAllowed\":true}", MediaType.APPLICATION_JSON));

        var response = service.forwardPost("/v1/biometric-policy/evaluate", Map.of("subjectType", "CLIENT"), request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("policyOutcome", "ALLOWED");
        server.verify();
    }

    @Test
    void returnsServiceUnavailableWhenLegacyDependencyFails() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Tenant-Id")).thenReturn("00000000-0000-0000-0000-000000000001");
        when(request.getHeader("X-Correlation-Id")).thenReturn("11111111-1111-1111-1111-111111111111");
        server.expect(once(), requestTo("http://localhost:8079/v1/biometric-policy/evaluate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.forwardPost("/v1/biometric-policy/evaluate", Map.of(), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE));
        server.verify();
    }
}
