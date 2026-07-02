package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.AfterEach;
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
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ShiftRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceDashboardRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceOverrideLogRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceRuleRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceServiceRetireTest {

    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceRuleRepository ruleRepository;
    @Mock private WorkspaceDashboardRepository dashboardRepository;
    @Mock private WorkspaceOverrideLogRepository overrideLogRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private FacilityRepository facilityRepository;
    @Mock private WorkspaceEligibilityEngine eligibilityEngine;
    @Mock private EventOutboxRepository outboxRepository;

    private WorkspaceService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WorkspaceService(workspaceRepository, ruleRepository, dashboardRepository,
                overrideLogRepository, shiftRepository, facilityRepository, eligibilityEngine, outboxRepository);
        TrustContextHolder.set(new TrustContext(tenantId, "actor-1", "DEVELOPER", "FACILITY_SETUP",
                "device-1", UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        when(ruleRepository.findByWorkspaceId(any())).thenReturn(List.of());
        when(dashboardRepository.findByWorkspaceIdAndActiveTrueOrderBySortOrderAsc(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private WorkspaceEntity workspace(UUID tenant) {
        FacilityEntity facility = new FacilityEntity();
        facility.setId(7L);
        WorkspaceEntity ws = new WorkspaceEntity();
        ws.setTenantId(tenant);
        ws.setName("OPD");
        ws.setWorkspaceType("OPD");
        ws.setActive(true);
        ws.setFacility(facility);
        return ws;
    }

    @Test
    void retire_deactivatesAndEmitsWorkspaceUpdatedEvent() {
        WorkspaceEntity ws = workspace(tenantId);
        UUID wsId = UUID.randomUUID();
        when(workspaceRepository.findById(wsId)).thenReturn(Optional.of(ws));
        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.retireWorkspace(wsId);

        assertFalse(response.active());
        assertFalse(ws.getActive());
        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals("tuso.workspace.updated", captor.getValue().getEventType());
        assertEquals("FACILITY", captor.getValue().getSubjectType());
        assertEquals("7", captor.getValue().getSubjectId());
    }

    @Test
    void retire_rejectsCrossTenant() {
        WorkspaceEntity ws = workspace(UUID.randomUUID());
        UUID wsId = UUID.randomUUID();
        when(workspaceRepository.findById(wsId)).thenReturn(Optional.of(ws));

        assertThrows(SecurityException.class, () -> service.retireWorkspace(wsId));
        verify(outboxRepository, never()).save(any());
    }
}
