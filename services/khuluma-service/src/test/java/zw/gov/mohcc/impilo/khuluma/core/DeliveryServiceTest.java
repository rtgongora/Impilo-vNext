package zw.gov.mohcc.impilo.khuluma.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.khuluma.domain.ChannelAdapterEntity;
import zw.gov.mohcc.impilo.khuluma.domain.DeliveryAttemptEntity;
import zw.gov.mohcc.impilo.khuluma.repository.ChannelAdapterRepository;
import zw.gov.mohcc.impilo.khuluma.repository.DeliveryAttemptRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    private static final UUID T = UUID.randomUUID();

    @Mock private ChannelAdapterRepository adapters;
    @Mock private DeliveryAttemptRepository attempts;

    private DeliveryService service() {
        return new DeliveryService(adapters, attempts);
    }

    @BeforeEach
    void echoSave() {
        lenient().when(attempts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(adapters.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ChannelAdapterEntity adapter(String channel, String status) {
        ChannelAdapterEntity a = new ChannelAdapterEntity();
        a.setChannel(channel);
        a.setStatus(status);
        return a;
    }

    @Test
    void in_app_is_delivered_natively() {
        List<DeliveryAttemptEntity> r = service().dispatch(T, "msg-1", "client-1", List.of("IN_APP"));
        assertThat(r).hasSize(1);
        assertThat(r.get(0).getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void unconfigured_external_channel_is_skipped_never_faked() {
        when(adapters.findByTenantIdAndChannel(T, "SMS")).thenReturn(Optional.empty());
        when(adapters.findByTenantIdAndChannel(T, "WHATSAPP"))
                .thenReturn(Optional.of(adapter("WHATSAPP", "NOT_CONFIGURED")));

        List<DeliveryAttemptEntity> r = service().dispatch(T, "msg-1", "+263...", List.of("SMS", "WHATSAPP"));

        assertThat(r).extracting(DeliveryAttemptEntity::getStatus)
                .containsExactly("SKIPPED_NOT_CONFIGURED", "SKIPPED_NOT_CONFIGURED");
        // The honesty property: a not-configured channel is NEVER reported as SENT/DELIVERED.
        assertThat(r).extracting(DeliveryAttemptEntity::getStatus).doesNotContain("SENT", "DELIVERED");
    }

    @Test
    void configured_external_channel_is_sent_via_its_provider() {
        when(adapters.findByTenantIdAndChannel(T, "SMS"))
                .thenReturn(Optional.of(adapter("SMS", "CONFIGURED")));

        DeliveryAttemptEntity a = service().dispatch(T, "msg-1", "+263...", List.of("SMS")).get(0);

        assertThat(a.getStatus()).isEqualTo("SENT");
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
