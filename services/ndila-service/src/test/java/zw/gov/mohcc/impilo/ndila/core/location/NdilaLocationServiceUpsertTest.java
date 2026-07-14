package zw.gov.mohcc.impilo.ndila.core.location;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.ndila.core.events.NdilaOutboxPublisher;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;
import zw.gov.mohcc.impilo.ndila.repository.NdilaLocationRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NdilaLocationServiceUpsertTest {

    private final NdilaLocationRepository repository = mock(NdilaLocationRepository.class);
    private final NdilaOutboxPublisher outbox = mock(NdilaOutboxPublisher.class);
    private final NdilaLocationService service = new NdilaLocationService(repository, outbox);

    private final UUID tenantId = UUID.randomUUID();
    private final String healthId = UUID.randomUUID().toString();

    private NdilaLocationService.CreateLocation cmd(String province) {
        return new NdilaLocationService.CreateLocation(
                tenantId, "vito", "client", healthId,
                "Client residence " + healthId, "delegated", "RESIDENCE",
                null, null, null,
                "12 Nelson Mandela Ave", "Harare", null, "Harare Central", province,
                "ZWE", "DELEGATED_VITO", "PRIVATE", null);
    }

    @Test
    void creates_a_new_location_when_the_owner_has_none() {
        when(repository.findByTenantIdAndOwnerServiceAndOwnerEntityId(tenantId, "vito", healthId))
                .thenReturn(List.of());

        NdilaLocationEntity result = service.upsertByOwner(cmd("Harare"), UUID.randomUUID());

        assertThat(result.getOwnerService()).isEqualTo("vito");
        assertThat(result.getOwnerEntityId()).isEqualTo(healthId);
        assertThat(result.getProvince()).isEqualTo("Harare");
        verify(repository).save(any(NdilaLocationEntity.class));
        verify(outbox).publish(any());
    }

    @Test
    void updates_the_existing_location_instead_of_duplicating_it() {
        NdilaLocationEntity existing = new NdilaLocationEntity(tenantId, "vito", "client", "old name", "RESIDENCE");
        existing.setOwnerEntityId(healthId);
        existing.setProvince("Manicaland");
        when(repository.findByTenantIdAndOwnerServiceAndOwnerEntityId(tenantId, "vito", healthId))
                .thenReturn(List.of(existing));

        NdilaLocationEntity result = service.upsertByOwner(cmd("Bulawayo"), UUID.randomUUID());

        // Same row, mutated — not a new one.
        assertThat(result).isSameAs(existing);
        assertThat(result.getProvince()).isEqualTo("Bulawayo");
        assertThat(result.getAddressLine1()).isEqualTo("12 Nelson Mandela Ave");
        verify(repository).save(existing);
        verify(outbox).publish(any());
    }
}
