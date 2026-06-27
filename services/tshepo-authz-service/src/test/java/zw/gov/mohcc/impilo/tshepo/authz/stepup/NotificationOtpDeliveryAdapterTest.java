package zw.gov.mohcc.impilo.tshepo.authz.stepup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies the authz→notification SMS-OTP hand-off: the OTP is forwarded to the notification
 * service with the companion context, and delivery fails closed when the service is unreachable
 * or rejects the request.
 */
@ExtendWith(MockitoExtension.class)
class NotificationOtpDeliveryAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    private AuthzProperties properties;
    private NotificationOtpDeliveryAdapter adapter;

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String ACTOR = "provider-7";
    private static final String OTP = "123456";

    @BeforeEach
    void setUp() {
        properties = new AuthzProperties();
        properties.setNotificationServiceUrl("http://notif:8200");
        properties.getOtpDelivery().setChannel("SMS");
        properties.getOtpDelivery().setTemplateKey("STEP_UP_OTP");
        adapter = new NotificationOtpDeliveryAdapter(restTemplate, properties);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deliver_postsOtpToNotificationServiceWithCompanionContext() {
        when(restTemplate.exchange(eq("http://notif:8200/internal/v1/notify"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body("{\"status\":\"PENDING\"}"));

        adapter.deliver(TENANT, ACTOR, OTP);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        org.mockito.Mockito.verify(restTemplate).exchange(
                eq("http://notif:8200/internal/v1/notify"), eq(HttpMethod.POST), captor.capture(), eq(String.class));

        HttpEntity<Map<String, Object>> sent = captor.getValue();
        Map<String, Object> body = sent.getBody();
        assertThat(body).containsEntry("channel", "SMS");
        assertThat(body).containsEntry("templateKey", "STEP_UP_OTP");
        assertThat(body).containsEntry("to", "actor:" + ACTOR);
        assertThat(body).containsEntry("inboxRecipient", "actor:" + ACTOR);
        assertThat(body.get("variables")).isEqualTo(Map.of("otp", OTP));
        // companion context propagated so notification-service RequestContextHolder.require() succeeds
        assertThat(sent.getHeaders().getFirst(CompanionHeaders.TENANT_ID)).isEqualTo(TENANT.toString());
        assertThat(sent.getHeaders().getFirst(CompanionHeaders.REQUEST_ID)).isNotBlank();
        assertThat(sent.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID)).isNotBlank();
    }

    @Test
    void deliver_failsClosedWhenNotificationServiceUnreachable() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertThatThrownBy(() -> adapter.deliver(TENANT, ACTOR, OTP))
                .isInstanceOf(StepUpUnavailableException.class)
                .hasMessageContaining("failed");
    }

    @Test
    void deliver_failsClosedOnNon2xx() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());

        assertThatThrownBy(() -> adapter.deliver(TENANT, ACTOR, OTP))
                .isInstanceOf(StepUpUnavailableException.class);
    }
}
