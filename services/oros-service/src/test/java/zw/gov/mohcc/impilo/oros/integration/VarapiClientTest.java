package zw.gov.mohcc.impilo.oros.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VarapiClient}, covering the configured and not-configured branches
 * (honest external-integration status) and lenient name extraction.
 */
@ExtendWith(MockitoExtension.class)
class VarapiClientTest {

    @Mock private RestTemplate restTemplate;

    @Test
    @DisplayName("not configured (blank base URL): isConfigured false and lookup returns empty without HTTP")
    void notConfigured() {
        VarapiClient client = new VarapiClient(restTemplate, "  ");

        assertThat(client.isConfigured()).isFalse();
        assertThat(client.lookupProviderName("prov-1")).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("blank provider id returns empty without HTTP")
    void blankProviderId() {
        VarapiClient client = new VarapiClient(restTemplate, "http://localhost:8083");
        assertThat(client.lookupProviderName("  ")).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("resolves name from an ApiResponse-style envelope (data.fullName)")
    void resolvesFromEnvelope() {
        VarapiClient client = new VarapiClient(restTemplate, "http://localhost:8083");
        when(restTemplate.exchange(eq("http://localhost:8083/v1/internal/providers/prov-1"),
                eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("fullName", "Dr Jane Doe"))));

        assertThat(client.lookupProviderName("prov-1")).contains("Dr Jane Doe");
    }

    @Test
    @DisplayName("resolves name from a bare provider object and composes given/family")
    void resolvesComposed() {
        VarapiClient client = new VarapiClient(restTemplate, "http://localhost:8083");
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("givenName", "John", "familyName", "Smith")));

        assertThat(client.lookupProviderName("prov-2")).contains("John Smith");
    }

    @Test
    @DisplayName("transport failure degrades to empty")
    void transportFailure() {
        VarapiClient client = new VarapiClient(restTemplate, "http://localhost:8083");
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertThat(client.lookupProviderName("prov-3")).isEmpty();
    }
}
