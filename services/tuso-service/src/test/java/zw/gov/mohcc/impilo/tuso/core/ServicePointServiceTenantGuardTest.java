package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ServicePointEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ServicePointRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression: TUSO service-point mutations must enforce tenant isolation,
 * matching the {@code FacilityService} convention. Without the guard a caller
 * could create/retire service points across tenant boundaries.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicePointServiceTenantGuardTest {

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
    void create_rejectsCrossTenantFacility() {
        FacilityEntity facility = new FacilityEntity();
        facility.setTenantId(UUID.randomUUID()); // different tenant than ctx
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));

        assertThrows(SecurityException.class, () -> service.create(ctx(tenantId), 7L,
                new ServicePointService.CreateServicePointRequest(
                        "Triage", "TRI", "GENERAL", null, null, null, null)));
        verify(repository, never()).save(any());
    }

    @Test
    void create_allowsSameTenant() {
        FacilityEntity facility = new FacilityEntity();
        facility.setTenantId(tenantId);
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        // Persisted entity carries a DB-assigned id (used by the outbox event).
        ServicePointEntity persisted = org.mockito.Mockito.mock(ServicePointEntity.class);
        when(persisted.getId()).thenReturn(UUID.randomUUID());
        when(persisted.getFacilityId()).thenReturn(7L);
        when(repository.save(any())).thenReturn(persisted);

        assertDoesNotThrow(() -> service.create(ctx(tenantId), 7L,
                new ServicePointService.CreateServicePointRequest(
                        "Triage", "TRI", "GENERAL", null, null, null, null)));
        verify(repository).save(any());
    }

    @Test
    void deactivate_rejectsCrossTenantServicePoint() {
        ServicePointEntity sp = new ServicePointEntity();
        sp.setTenantId(UUID.randomUUID()); // different tenant
        UUID spId = UUID.randomUUID();
        when(repository.findById(spId)).thenReturn(Optional.of(sp));

        assertThrows(SecurityException.class, () -> service.deactivate(ctx(tenantId), spId));
        verify(repository, never()).save(any());
    }
}
