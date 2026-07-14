package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ClientAddressDelegationPublisherTest {

    private final EventOutboxRepository outbox = mock(EventOutboxRepository.class);
    private final ClientAddressDelegationPublisher publisher =
            new ClientAddressDelegationPublisher(outbox, new ObjectMapper());

    private ClientEntity client(String addressJson) {
        ClientEntity c = new ClientEntity();
        c.setHealthId(UUID.randomUUID());
        c.setTenantId(UUID.randomUUID());
        c.setAddress(addressJson);
        return c;
    }

    @Test
    void emits_a_client_address_upserted_event_with_the_address_payload() {
        ClientEntity c = client("{\"addressLine1\":\"12 Nelson Mandela Ave\",\"city\":\"Harare\",\"province\":\"Harare\"}");

        publisher.emitAddressUpserted(c);

        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outbox).save(captor.capture());
        EventOutboxEntity event = captor.getValue();
        assertThat(event.getAggregateType()).isEqualTo("CLIENT");
        assertThat(event.getEventType()).isEqualTo("CLIENT_ADDRESS_UPSERTED");
        assertThat(event.getAggregateId()).isEqualTo(c.getHealthId().toString());
        assertThat(event.getPayload()).contains("CLIENT_ADDRESS_UPSERTED")
                .contains(c.getHealthId().toString())
                .contains("Nelson Mandela");
    }

    @Test
    void does_not_emit_when_address_is_blank_or_empty_object() {
        publisher.emitAddressUpserted(client("{}"));
        publisher.emitAddressUpserted(client("   "));
        publisher.emitAddressUpserted(client(null));
        verify(outbox, never()).save(any());
    }

    @Test
    void does_not_emit_when_client_has_no_health_id() {
        ClientEntity c = new ClientEntity();
        c.setAddress("{\"city\":\"Harare\"}");
        publisher.emitAddressUpserted(c);
        verify(outbox, never()).save(any());
    }
}
