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
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityConfigurationDto.ChecklistItem;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityConfigurationDto.ConfigurationSummary;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityConfigurationDto.QueueDefinitionView;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityConfigurationDto.SetupChecklist;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ServicePointEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityConfigVersionRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityContactRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityReadinessRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityUnitRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ServicePointRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Facility Configuration Console read-models. Verifies honest configuration-state
 * derivation, queue-definition derivation from active service points, checklist completeness and the
 * configuration-publish outbox event.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FacilityConfigurationServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private ServicePointRepository servicePointRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private FacilityUnitRepository facilityUnitRepository;
    @Mock private FacilityReadinessRepository readinessRepository;
    @Mock private FacilityContactRepository contactRepository;
    @Mock private FacilityConfigVersionRepository configVersionRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private FacilityConfigurationService service;
    private final UUID tenantId = UUID.randomUUID();
    private static final Long FAC = 42L;

    private TrustContext ctx() {
        return new TrustContext(tenantId, "actor-1", "DEVELOPER", "FACILITY_SETUP", "device-1",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL);
    }

    private FacilityEntity facility(FacilityRegulatoryStatus status, String code) {
        FacilityEntity f = new FacilityEntity();
        f.setId(FAC);
        f.setTenantId(tenantId);
        f.setFacilityUuid(UUID.randomUUID());
        f.setName("Test Clinic");
        f.setFacilityCode(code);
        f.setFacilityType("CLINIC");
        f.setRegulatoryStatus(status);
        return f;
    }

    @BeforeEach
    void setUp() {
        service = new FacilityConfigurationService(facilityRepository, servicePointRepository,
                workspaceRepository, facilityUnitRepository, readinessRepository, contactRepository,
                configVersionRepository, outboxRepository);
        when(readinessRepository.findByFacilityId(FAC)).thenReturn(Optional.empty());
        when(contactRepository.findByFacilityId(FAC)).thenReturn(List.of());
        when(configVersionRepository.findLatestActive(FAC)).thenReturn(Optional.empty());
        when(facilityUnitRepository.findByFacilityIdOrderByCreatedAtAsc(FAC)).thenReturn(List.of());
    }

    @Test
    void importedFacilityStaysPendingUntilRequiredSectionsComplete() {
        when(facilityRepository.findById(FAC))
                .thenReturn(Optional.of(facility(FacilityRegulatoryStatus.IMPORTED_PENDING_CONFIGURATION, "CODE-1")));
        when(servicePointRepository.countByFacilityIdAndActiveTrue(FAC)).thenReturn(0L);
        when(workspaceRepository.findByFacilityIdAndActiveTrue(FAC)).thenReturn(List.of());

        ConfigurationSummary s = service.getConfigurationSummary(ctx(), FAC);

        assertEquals("IMPORTED_PENDING_CONFIGURATION", s.configurationState());
        assertFalse(s.setupComplete());
        assertTrue(s.missingSections().contains("service_points"));
        assertTrue(s.missingSections().contains("workspaces"));
    }

    @Test
    void importedFacilityBecomesConfiguredWhenRequiredSectionsComplete() {
        when(facilityRepository.findById(FAC))
                .thenReturn(Optional.of(facility(FacilityRegulatoryStatus.IMPORTED_PENDING_CONFIGURATION, "CODE-1")));
        when(servicePointRepository.countByFacilityIdAndActiveTrue(FAC)).thenReturn(2L);
        when(workspaceRepository.findByFacilityIdAndActiveTrue(FAC)).thenReturn(List.of(mock(WorkspaceEntity.class)));

        ConfigurationSummary s = service.getConfigurationSummary(ctx(), FAC);

        assertEquals("CONFIGURED", s.configurationState());
        assertTrue(s.setupComplete());
        assertTrue(s.missingSections().isEmpty());
        assertEquals(2L, s.activeQueueDefinitionCount());
    }

    @Test
    void notStartedWhenNothingConfigured() {
        when(facilityRepository.findById(FAC))
                .thenReturn(Optional.of(facility(FacilityRegulatoryStatus.REGISTERED_ACTIVE, "CODE-1")));
        when(servicePointRepository.countByFacilityIdAndActiveTrue(FAC)).thenReturn(0L);
        when(workspaceRepository.findByFacilityIdAndActiveTrue(FAC)).thenReturn(List.of());

        ConfigurationSummary s = service.getConfigurationSummary(ctx(), FAC);
        assertEquals("NOT_STARTED", s.configurationState());
    }

    @Test
    void queueDefinitionsDeriveFromActiveServicePoints() {
        when(facilityRepository.findById(FAC))
                .thenReturn(Optional.of(facility(FacilityRegulatoryStatus.REGISTERED_ACTIVE, "CODE-1")));
        ServicePointEntity sp = mock(ServicePointEntity.class);
        when(sp.getId()).thenReturn(UUID.randomUUID());
        when(sp.getName()).thenReturn("Triage");
        when(sp.getServicePointType()).thenReturn("TRIAGE");
        when(sp.isActive()).thenReturn(true);
        when(sp.getStatus()).thenReturn("ACTIVE");
        when(servicePointRepository.findByFacilityIdAndActiveTrueOrderByCreatedAtAsc(FAC)).thenReturn(List.of(sp));

        List<QueueDefinitionView> defs = service.getQueueDefinitions(ctx(), FAC);
        assertEquals(1, defs.size());
        assertEquals("Triage", defs.get(0).displayName());
        assertTrue(defs.get(0).active());
        assertTrue(defs.get(0).priorityFlag(), "TRIAGE queue should be flagged priority");
    }

    @Test
    void retiredServicePointDoesNotProduceActiveQueueDefinition() {
        when(facilityRepository.findById(FAC))
                .thenReturn(Optional.of(facility(FacilityRegulatoryStatus.REGISTERED_ACTIVE, "CODE-1")));
        ServicePointEntity retired = mock(ServicePointEntity.class);
        when(retired.getId()).thenReturn(UUID.randomUUID());
        when(retired.getName()).thenReturn("Old Desk");
        when(retired.getServicePointType()).thenReturn("CONSULTATION");
        when(retired.isActive()).thenReturn(false);
        when(retired.getStatus()).thenReturn("RETIRED");
        when(servicePointRepository.findByFacilityIdAndActiveTrueOrderByCreatedAtAsc(FAC))
                .thenReturn(List.of(retired));

        List<QueueDefinitionView> defs = service.getQueueDefinitions(ctx(), FAC);
        assertEquals(1, defs.size());
        assertFalse(defs.get(0).active(), "Retired service point must not yield an active queue definition");
    }

    @Test
    void setupChecklistReflectsMissingAndComplete() {
        when(facilityRepository.findById(FAC))
                .thenReturn(Optional.of(facility(FacilityRegulatoryStatus.REGISTERED_ACTIVE, null)));
        when(servicePointRepository.countByFacilityIdAndActiveTrue(FAC)).thenReturn(0L);
        when(workspaceRepository.findByFacilityIdAndActiveTrue(FAC)).thenReturn(List.of());

        SetupChecklist checklist = service.getSetupChecklist(ctx(), FAC);
        assertFalse(checklist.setupComplete());
        assertEquals("MISSING", itemStatus(checklist, "facility_code"));
        assertEquals("MISSING", itemStatus(checklist, "service_points"));
        assertEquals("COMPLETE", itemStatus(checklist, "identity_imported"));
        // Honest: regulatory compliance / workforce are pending, never faked complete.
        assertEquals("PENDING", itemStatus(checklist, "regulatory_compliance"));
        assertEquals("PENDING", itemStatus(checklist, "workforce"));
    }

    @Test
    void publishConfigurationUpdateEmitsOutboxEvent() {
        when(facilityRepository.findById(FAC))
                .thenReturn(Optional.of(facility(FacilityRegulatoryStatus.REGISTERED_ACTIVE, "CODE-1")));
        when(servicePointRepository.countByFacilityIdAndActiveTrue(FAC)).thenReturn(1L);
        when(workspaceRepository.findByFacilityIdAndActiveTrue(FAC)).thenReturn(List.of(mock(WorkspaceEntity.class)));

        service.publishConfigurationUpdate(ctx(), FAC);

        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        EventOutboxEntity event = captor.getValue();
        assertEquals("FacilityQueueConfigurationChanged", event.getEventType());
        assertEquals("FACILITY", event.getSubjectType());
        assertEquals(String.valueOf(FAC), event.getSubjectId());
    }

    private static String itemStatus(SetupChecklist checklist, String key) {
        return checklist.items().stream()
                .filter(i -> i.key().equals(key))
                .map(ChecklistItem::status)
                .findFirst().orElse("ABSENT");
    }
}
