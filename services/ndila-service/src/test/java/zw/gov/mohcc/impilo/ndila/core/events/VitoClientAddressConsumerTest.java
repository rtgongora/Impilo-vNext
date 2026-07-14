package zw.gov.mohcc.impilo.ndila.core.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.ndila.core.location.NdilaLocationService;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VitoClientAddressConsumerTest {

    private final NdilaLocationService locationService = mock(NdilaLocationService.class);
    private final VitoClientAddressConsumer consumer =
            new VitoClientAddressConsumer(locationService, new ObjectMapper());

    private final UUID tenantId = UUID.randomUUID();
    private final String healthId = UUID.randomUUID().toString();

    @Test
    void materializes_a_location_from_a_client_address_upserted_event() {
        when(locationService.upsertByOwner(any(), any())).thenReturn(mock(NdilaLocationEntity.class));
        String msg = "{\"eventType\":\"CLIENT_ADDRESS_UPSERTED\",\"healthId\":\"" + healthId + "\","
                + "\"tenantId\":\"" + tenantId + "\",\"address\":{\"addressLine1\":\"12 Nelson Mandela Ave\","
                + "\"city\":\"Harare\",\"district\":\"Harare Central\",\"province\":\"Harare\"}}";

        consumer.onVitoIdentityEvent(msg);

        ArgumentCaptor<NdilaLocationService.CreateLocation> captor =
                ArgumentCaptor.forClass(NdilaLocationService.CreateLocation.class);
        verify(locationService).upsertByOwner(captor.capture(), any());
        NdilaLocationService.CreateLocation cmd = captor.getValue();
        assertThat(cmd.ownerService()).isEqualTo("vito");
        assertThat(cmd.ownerEntityType()).isEqualTo("client");
        assertThat(cmd.ownerEntityId()).isEqualTo(healthId);
        assertThat(cmd.tenantId()).isEqualTo(tenantId);
        assertThat(cmd.addressLine1()).isEqualTo("12 Nelson Mandela Ave");
        assertThat(cmd.locality()).isEqualTo("Harare");
        assertThat(cmd.district()).isEqualTo("Harare Central");
        assertThat(cmd.province()).isEqualTo("Harare");
        assertThat(cmd.source()).isEqualTo("DELEGATED_VITO");
    }

    @Test
    void ignores_other_event_types_on_the_shared_stream() {
        consumer.onVitoIdentityEvent("{\"eventType\":\"CLIENT_CREATED\",\"cpid\":\"abc\"}");
        verify(locationService, never()).upsertByOwner(any(), any());
    }

    @Test
    void malformed_message_does_not_throw() {
        consumer.onVitoIdentityEvent("not-json");
        verify(locationService, never()).upsertByOwner(any(), any());
    }
}
