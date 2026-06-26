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
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityUnitRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression: TUSO facility-unit creation must enforce tenant isolation,
 * matching the {@code FacilityService} convention.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FacilityUnitServiceTenantGuardTest {

    @Mock private FacilityUnitRepository unitRepository;
    @Mock private FacilityRepository facilityRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private FacilityUnitService service;
    private final UUID tenantId = UUID.randomUUID();

    private TrustContext ctx(UUID tenant) {
        return new TrustContext(tenant, "actor-1", "DEVELOPER", "FACILITY_SETUP", "device-1",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL);
    }

    @BeforeEach
    void setUp() {
        service = new FacilityUnitService(unitRepository, facilityRepository, outboxRepository);
        when(unitRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void create_rejectsCrossTenantFacility() {
        FacilityEntity facility = new FacilityEntity();
        facility.setTenantId(UUID.randomUUID()); // different tenant
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));

        assertThrows(SecurityException.class, () -> service.create(ctx(tenantId), 7L,
                new FacilityUnitService.CreateUnitRequest("OPD", "DEPARTMENT", "GENERAL", false, null)));
        verify(unitRepository, never()).save(any());
    }

    @Test
    void create_allowsSameTenant() {
        FacilityEntity facility = new FacilityEntity();
        facility.setTenantId(tenantId);
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));

        assertDoesNotThrow(() -> service.create(ctx(tenantId), 7L,
                new FacilityUnitService.CreateUnitRequest("OPD", "DEPARTMENT", "GENERAL", false, null)));
    }
}
