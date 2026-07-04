package zw.gov.mohcc.impilo.live.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Proves the exact document-service routes and the system trust headers stamped per call. */
@ExtendWith(MockitoExtension.class)
class DocumentServiceClientTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private RestTemplate restTemplate;

    private DocumentServiceClient client;

    @BeforeEach
    void setUp() {
        client = new DocumentServiceClient(restTemplate, "http://localhost:8093/");
    }

    @Test
    void registerExternal_postsToRegisterExternalRouteWithTenantHeaders() {
        ArgumentCaptor<HttpEntity<Object>> entity = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(
                eq("http://localhost:8093/v1/internal/objects/register-external"),
                eq(HttpMethod.POST), entity.capture(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("objectId", "obj-1"))));

        Map<String, Object> response = client.registerExternal(TENANT_ID,
                Map.of("bucket", "impilo-recordings", "key", "rtc/x.mp4"));

        assertThat(response).containsKey("data");
        assertThat(entity.getValue().getHeaders().getFirst(TrustContext.H_TENANT_ID))
                .isEqualTo(TENANT_ID.toString());
        assertThat(entity.getValue().getHeaders().getFirst(TrustContext.H_ACTOR_ID))
                .isEqualTo(DocumentServiceClient.SYSTEM_ACTOR);
        assertThat(entity.getValue().getHeaders().getFirst(TrustContext.H_CORRELATION_ID)).isNotBlank();
        assertThat(entity.getValue().getBody()).isEqualTo(
                Map.of("bucket", "impilo-recordings", "key", "rtc/x.mp4"));
    }

    @Test
    void getSignedUrl_callsObjectSignedUrlRoute() {
        when(restTemplate.exchange(
                eq("http://localhost:8093/v1/internal/objects/obj-1/signed-url"),
                eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("url", "https://signed.local/x"))));

        Map<String, Object> response = client.getSignedUrl(TENANT_ID, "obj-1");

        assertThat(response).containsKey("data");
    }

    @Test
    void failures_returnEmptyMapNotException() {
        when(restTemplate.exchange(any(String.class), any(), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertThat(client.getSignedUrl(TENANT_ID, "obj-1")).isEmpty();
    }
}
