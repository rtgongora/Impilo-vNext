package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySetupStateEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityUnitEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySetupStateRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityUnitRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ServicePointRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FacilitySetupServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilitySetupStateRepository setupStateRepository;
    @Mock private FacilityUnitRepository facilityUnitRepository;
    @Mock private ServicePointRepository servicePointRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private FacilitySetupService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FacilitySetupService(facilityRepository, setupStateRepository,
                facilityUnitRepository, servicePointRepository, outboxRepository);

        TrustContextHolder.set(new TrustContext(
                tenantId, "actor-1", "DEVELOPER", "FACILITY_SETUP", "device-1",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));

        FacilityEntity facility = new FacilityEntity();
        facility.setTenantId(tenantId);
        when(facilityRepository.findById(anyLong())).thenReturn(Optional.of(facility));
        when(facilityUnitRepository.findByFacilityIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());
        when(servicePointRepository.countByFacilityIdAndActiveTrue(anyLong())).thenReturn(0L);
        when(setupStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void getOrCreateState_createsWhenAbsent() {
        when(setupStateRepository.findByFacilityId(anyLong())).thenReturn(Optional.empty());
        FacilitySetupStateEntity state = service.getOrCreateState(TrustContextHolder.require(), 42L);
        assertEquals(42L, state.getFacilityId());
        assertEquals(tenantId, state.getTenantId());
        assertFalse(state.isGoLive());
    }

    @Test
    void goLive_blockedWhenPrerequisitesIncomplete() {
        FacilitySetupStateEntity state = new FacilitySetupStateEntity();
        state.setFacilityId(42L);
        state.setTenantId(tenantId);
        when(setupStateRepository.findByFacilityId(anyLong())).thenReturn(Optional.of(state));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.advanceStep(TrustContextHolder.require(), 42L,
                        FacilitySetupService.SetupStep.GO_LIVE, true));
        assertTrue(ex.getMessage().contains("prerequisites"));
    }

    @Test
    void goLive_succeedsWhenAllPrerequisitesMet() {
        FacilitySetupStateEntity state = fullyConfigured();
        when(setupStateRepository.findByFacilityId(anyLong())).thenReturn(Optional.of(state));
        // Reconcile is now bidirectional: the configured dept/service-point flags must be
        // backed by live entities, otherwise getOrCreateState would clear them.
        FacilityUnitEntity unit = new FacilityUnitEntity();
        when(facilityUnitRepository.findByFacilityIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of(unit));
        when(servicePointRepository.countByFacilityIdAndActiveTrue(anyLong())).thenReturn(1L);

        FacilitySetupStateEntity result = service.advanceStep(TrustContextHolder.require(), 42L,
                FacilitySetupService.SetupStep.GO_LIVE, true);
        assertTrue(result.isGoLive());
        assertNotNull(result.getGoLiveAt());
    }

    @Test
    void nextStep_pointsToFirstIncomplete() {
        FacilitySetupStateEntity state = new FacilitySetupStateEntity();
        state.setDepartmentsConfigured(true);
        assertEquals(FacilitySetupService.SetupStep.SERVICE_POINTS, service.nextStep(state));
    }

    @Test
    void getOrCreateState_rejectsCrossTenantFacility() {
        FacilityEntity otherTenant = new FacilityEntity();
        otherTenant.setTenantId(UUID.randomUUID());
        when(facilityRepository.findById(anyLong())).thenReturn(Optional.of(otherTenant));

        assertThrows(SecurityException.class,
                () -> service.getOrCreateState(TrustContextHolder.require(), 42L));
    }

    @Test
    void getOrCreateState_reconcilesFlagsBackToFalseWhenEntitiesRemoved() {
        // State says configured, but no live departments/service-points remain.
        FacilitySetupStateEntity state = new FacilitySetupStateEntity();
        state.setFacilityId(42L);
        state.setTenantId(tenantId);
        state.setDepartmentsConfigured(true);
        state.setServicePointsConfigured(true);
        when(setupStateRepository.findByFacilityId(anyLong())).thenReturn(Optional.of(state));
        // setUp already mocks units empty and service-point count 0.

        FacilitySetupStateEntity result = service.getOrCreateState(TrustContextHolder.require(), 42L);
        assertFalse(result.isDepartmentsConfigured());
        assertFalse(result.isServicePointsConfigured());
    }

    private FacilitySetupStateEntity fullyConfigured() {
        FacilitySetupStateEntity s = new FacilitySetupStateEntity();
        s.setFacilityId(42L);
        s.setTenantId(tenantId);
        s.setDepartmentsConfigured(true);
        s.setServicePointsConfigured(true);
        s.setQueuesConfigured(true);
        s.setWorkflowsConfigured(true);
        s.setWorkforceLinked(true);
        s.setOrosRoutingConfigured(true);
        s.setKhulumaChannelsConfigured(true);
        s.setFundoReady(true);
        return s;
    }
}
