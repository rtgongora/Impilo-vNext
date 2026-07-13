package zw.gov.mohcc.impilo.khuluma.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.khuluma.client.NotificationDeliveryClient;
import zw.gov.mohcc.impilo.khuluma.core.DeliveryService.DispatchContent;
import zw.gov.mohcc.impilo.khuluma.domain.ChannelAdapterEntity;
import zw.gov.mohcc.impilo.khuluma.domain.DeliveryAttemptEntity;
import zw.gov.mohcc.impilo.khuluma.repository.ChannelAdapterRepository;
import zw.gov.mohcc.impilo.khuluma.repository.DeliveryAttemptRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    private static final UUID T = UUID.randomUUID();

    @Mock private ChannelAdapterRepository adapters;
    @Mock private DeliveryAttemptRepository attempts;
    @Mock private NotificationDeliveryClient notification;

    private DeliveryService service() {
        return new DeliveryService(adapters, attempts, notification);
    }

    private DispatchContent withTemplate() {
        return new DispatchContent("KHULUMA_MESSAGE", Map.of("body", "hi"), null, "REMINDER");
    }

    @BeforeEach
    void echoSave() {
        lenient().when(attempts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(adapters.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void in_app_is_delivered_natively() {
        List<DeliveryAttemptEntity> r = service().dispatch(T, "msg-1", "client-1", List.of("IN_APP"));
        assertThat(r).hasSize(1);
        assertThat(r.get(0).getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void external_channel_without_template_is_skipped_never_faked() {
        // G31: notification-service is template-driven; no templateKey → honest skip, no fabricated send.
        List<DeliveryAttemptEntity> r = service().dispatch(T, "msg-1", "+263...", List.of("SMS", "WHATSAPP"));

        assertThat(r).extracting(DeliveryAttemptEntity::getStatus)
                .containsExactly("SKIPPED_NO_TEMPLATE", "SKIPPED_NO_TEMPLATE");
        assertThat(r).extracting(DeliveryAttemptEntity::getStatus).doesNotContain("SENT", "DELIVERED");
    }

    @Test
    void external_channel_with_template_delegates_to_notification_service() {
        when(notification.deliver(eq("SMS"), anyString(), eq("KHULUMA_MESSAGE"), any(), any(), any()))
                .thenReturn(new NotificationDeliveryClient.DeliveryResult(true, "nid-1", null));

        DeliveryAttemptEntity a = service()
                .dispatch(T, "msg-1", "+263...", List.of("SMS"), withTemplate()).get(0);

        assertThat(a.getStatus()).isEqualTo("SENT");
        assertThat(a.getDetail()).contains("notification-service");
    }

    @Test
    void external_delivery_failure_is_recorded_failed_never_faked() {
        when(notification.deliver(eq("SMS"), anyString(), anyString(), any(), any(), any()))
                .thenReturn(new NotificationDeliveryClient.DeliveryResult(false, null, "provider down"));

        DeliveryAttemptEntity a = service()
                .dispatch(T, "msg-1", "+263...", List.of("SMS"), withTemplate()).get(0);

        assertThat(a.getStatus()).isEqualTo("FAILED");
        assertThat(a.getStatus()).isNotEqualTo("DELIVERED");
    }

    @Test
    void unknown_channel_is_skipped_with_no_adapter() {
        DeliveryAttemptEntity a = service().dispatch(T, "m", "r", List.of("CARRIER_PIGEON")).get(0);
        assertThat(a.getStatus()).isEqualTo("SKIPPED_NO_ADAPTER");
    }

    @Test
    void configure_adapter_rejects_unknown_channel() {
        assertThatThrownBy(() -> service().configureAdapter(T, "BOGUS", "CONFIGURED", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configure_adapter_persists_status_and_provider() {
        when(adapters.findByTenantIdAndChannel(T, "SMS")).thenReturn(Optional.empty());
        ChannelAdapterEntity a = service().configureAdapter(T, "sms", "configured", "twilio");
        assertThat(a.getChannel()).isEqualTo("SMS");
        assertThat(a.getStatus()).isEqualTo("CONFIGURED");
        assertThat(a.getProvider()).isEqualTo("twilio");
    }
}
