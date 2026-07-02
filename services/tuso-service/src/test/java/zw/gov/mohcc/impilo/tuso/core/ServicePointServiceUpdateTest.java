package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ServicePointEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ServicePointRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicePointServiceUpdateTest {

    @Mock private ServicePointRepository repository;
    @Mock private FacilityRepository facilityRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private ServicePointService service;
    private final UUID tenantId = UUID.randomUUID();

    private TrustContext ctx(UUID tenant) {
        return new TrustContext(tenant, "actor-1", "DEVELOPER", "FACILITY_SETUP", "device-1",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL);
    }

    @BeforeEach
    void setUp() {
        service = new ServicePointService(repository, facilityRepository, outboxRepository);
    }

    @Test
    void update_appliesPatchAndEmitsUpdatedEvent() {
        ServicePointEntity sp = new ServicePointEntity();
        sp.setTenantId(tenantId);
        sp.setFacilityId(7L);
        sp.setName("Old");
        UUID spId = UUID.randomUUID();
        when(repository.findById(spId)).thenReturn(Optional.of(sp));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(ctx(tenantId), spId, new ServicePointService.UpdateServicePointRequest(
                "New Name", null, "TRIAGE", null, null, null, null, null, null));

        assertEquals("New Name", sp.getName());
        assertEquals("TRIAGE", sp.getServicePointType());
        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals("SERVICE_POINT_UPDATED", captor.getValue().getEventType());
        assertEquals("FACILITY", captor.getValue().getSubjectType());
        assertEquals("7", captor.getValue().getSubjectId());
    }

    @Test
    void update_rejectsCrossTenant() {
        ServicePointEntity sp = new ServicePointEntity();
        sp.setTenantId(UUID.randomUUID());
        UUID spId = UUID.randomUUID();
        when(repository.findById(spId)).thenReturn(Optional.of(sp));

        assertThrows(SecurityException.class, () -> service.update(ctx(tenantId), spId,
                new ServicePointService.UpdateServicePointRequest(
                        "x", null, null, null, null, null, null, null, null)));
        verify(repository, never()).save(any());
    }
}
