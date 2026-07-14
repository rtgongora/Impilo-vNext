package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NdilaLocationConsumerTest {

    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final NdilaLocationConsumer consumer = new NdilaLocationConsumer(clientRepository, new ObjectMapper());

    private final UUID tenantId = UUID.randomUUID();
    private final UUID healthId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();

    private String event(String ownerService, String ownerEntityType) {
        return "{\"ownerService\":\"" + ownerService + "\",\"ownerEntityType\":\"" + ownerEntityType + "\","
                + "\"ownerEntityId\":\"" + healthId + "\",\"tenantId\":\"" + tenantId + "\","
                + "\"locationId\":\"" + locationId + "\"}";
    }

    @Test
    void caches_the_location_id_on_a_vito_client() {
        ClientEntity client = new ClientEntity();
        when(clientRepository.findByTenantIdAndHealthId(tenantId, healthId)).thenReturn(Optional.of(client));

        consumer.onLocationEvent(event("vito", "client"));

        assertThat(client.getNdilaLocationId()).isEqualTo(locationId);
        verify(clientRepository).save(client);
    }

    @Test
    void ignores_locations_not_owned_by_a_vito_client() {
        consumer.onLocationEvent(event("indawo", "site"));
        verify(clientRepository, never()).findByTenantIdAndHealthId(any(), any());
        verify(clientRepository, never()).save(any());
    }

    @Test
    void is_idempotent_when_the_id_is_already_cached() {
        ClientEntity client = new ClientEntity();
        client.setNdilaLocationId(locationId);
        when(clientRepository.findByTenantIdAndHealthId(tenantId, healthId)).thenReturn(Optional.of(client));

        consumer.onLocationEvent(event("vito", "client"));

        verify(clientRepository, never()).save(any());
    }

    @Test
    void malformed_message_does_not_throw() {
        consumer.onLocationEvent("not-json");
        verify(clientRepository, never()).save(any());
    }
}
