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
import zw.gov.mohcc.impilo.tuso.api.dto.AlertResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.ControlTowerAggregateResponse;
import zw.gov.mohcc.impilo.tuso.persistence.entity.AlertEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.AlertRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.OccupancySnapshotRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ShiftRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.TelemetryEventRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ControlTowerAggregateTest {

    @Mock private TelemetryEventRepository telemetryRepository;
    @Mock private OccupancySnapshotRepository occupancyRepository;
    @Mock private AlertRepository alertRepository;
    @Mock private FacilityRepository facilityRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private AlertRuleEngine alertRuleEngine;

    private ControlTowerService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ControlTowerService(telemetryRepository, occupancyRepository, alertRepository,
                facilityRepository, workspaceRepository, shiftRepository, alertRuleEngine);
        TrustContextHolder.set(new TrustContext(
                tenantId, "actor-1", "DEVELOPER", "FACILITY_OPS", "device-1",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void aggregate_rowDetail_returnsPerFacilityRows() {
        FacilityEntity f = new FacilityEntity();
        f.setTenantId(tenantId);
        when(facilityRepository.findByTenantId(tenantId)).thenReturn(List.of(f));
        when(alertRepository.countOpenByTenant(tenantId)).thenReturn(3L);
        when(alertRepository.countOpenByFacility(any())).thenReturn(2L);
        when(occupancyRepository.findLatestByFacility(any())).thenReturn(null);

        ControlTowerAggregateResponse resp = service.aggregate(TrustContextHolder.require(), false);
        assertEquals(1, resp.facilityCount());
        assertEquals(3, resp.openAlertCount());
        assertEquals("ROW_DETAIL", resp.visibility());
        assertEquals(1, resp.facilities().size());
        assertEquals(2, resp.facilities().get(0).openAlerts());
    }

    @Test
    void aggregate_aggregateOnly_hidesRows() {
        FacilityEntity f = new FacilityEntity();
        f.setTenantId(tenantId);
        when(facilityRepository.findByTenantId(tenantId)).thenReturn(List.of(f));
        when(alertRepository.countOpenByTenant(tenantId)).thenReturn(5L);

        ControlTowerAggregateResponse resp = service.aggregate(TrustContextHolder.require(), true);
        assertEquals("AGGREGATE_ONLY", resp.visibility());
        assertEquals(5, resp.openAlertCount());
        assertTrue(resp.facilities().isEmpty());
    }

    @Test
    void acknowledgeAlert_setsAcknowledgedState() {
        FacilityEntity f = new FacilityEntity();
        f.setTenantId(tenantId);
        AlertEntity alert = new AlertEntity();
        alert.setFacility(f);
        alert.setTenantId(tenantId);
        alert.setStatus("OPEN");
        UUID alertId = UUID.randomUUID();
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AlertResponse resp = service.acknowledgeAlert(TrustContextHolder.require(), alertId);
        assertEquals("ACKNOWLEDGED", resp.status());
        assertEquals("actor-1", resp.acknowledgedBy());
        assertNotNull(resp.acknowledgedAt());
    }

    @Test
    void acknowledgeAlert_rejectsCrossTenantAlert() {
        FacilityEntity f = new FacilityEntity();
        f.setTenantId(UUID.randomUUID());
        AlertEntity alert = new AlertEntity();
        alert.setFacility(f);
        alert.setTenantId(UUID.randomUUID()); // a different tenant than the trust context
        alert.setStatus("OPEN");
        UUID alertId = UUID.randomUUID();
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));

        assertThrows(SecurityException.class,
                () -> service.acknowledgeAlert(TrustContextHolder.require(), alertId));
        assertEquals("OPEN", alert.getStatus()); // unchanged
    }
}
