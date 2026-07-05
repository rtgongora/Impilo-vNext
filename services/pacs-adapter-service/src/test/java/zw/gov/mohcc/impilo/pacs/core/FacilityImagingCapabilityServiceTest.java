package zw.gov.mohcc.impilo.pacs.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pacs.api.dto.FacilityImagingCapabilityRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.ImagingModalityRequest;
import zw.gov.mohcc.impilo.pacs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.FacilityImagingCapabilityEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingModalityEntity;
import zw.gov.mohcc.impilo.pacs.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.FacilityImagingCapabilityRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingAccessAuditRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingModalityRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FacilityImagingCapabilityServiceTest {

    @Mock private FacilityImagingCapabilityRepository capabilityRepository;
    @Mock private ImagingModalityRepository modalityRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ImagingAccessAuditRepository accessAuditRepository;

    private FacilityImagingCapabilityService service;

    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FacilityImagingCapabilityService(
                capabilityRepository, modalityRepository, outboxRepository,
                accessAuditRepository, new ObjectMapper());
    }

    @Test
    void upsertCapability_createsRecordAndEmitsOutboxAndAudit() {
        when(capabilityRepository.findByTenantIdAndFacilityId(tenant, "fac-1")).thenReturn(Optional.empty());
        when(capabilityRepository.save(any(FacilityImagingCapabilityEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FacilityImagingCapabilityRequest request = new FacilityImagingCapabilityRequest();
        request.setDeploymentMode("gateway_store_forward");
        request.setLocalGatewayAvailable(true);
        request.setMwlSupported(true);
        request.setDefaultArchiveTarget("regional-pacs.example");

        FacilityImagingCapabilityEntity saved =
                service.upsertCapability(tenant, "fac-1", request, "actor-1", "ADMIN");

        assertEquals("GATEWAY_STORE_FORWARD", saved.getDeploymentMode());
        assertTrue(saved.isLocalGatewayAvailable());
        assertEquals("regional-pacs.example", saved.getDefaultArchiveTarget());
        verify(accessAuditRepository).save(any());
        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertEquals(FacilityImagingCapabilityService.EVENT_CAPABILITY_UPDATED, outbox.getValue().getEventType());
    }

    @Test
    void upsertCapability_rejectsUnknownMode() {
        FacilityImagingCapabilityRequest request = new FacilityImagingCapabilityRequest();
        request.setDeploymentMode("MAGIC_CLOUD");
        assertThrows(IllegalArgumentException.class,
                () -> service.upsertCapability(tenant, "fac-1", request, "actor-1", "ADMIN"));
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void registerModality_rejectsDuplicateAeTitleAtFacility() {
        ImagingModalityEntity existing = new ImagingModalityEntity();
        existing.setId(9L);
        when(modalityRepository.findByTenantIdAndFacilityIdAndAeTitle(tenant, "fac-1", "XR_ROOM1"))
                .thenReturn(Optional.of(existing));

        ImagingModalityRequest request = new ImagingModalityRequest();
        request.setModalityType("DX");
        request.setName("X-ray room 1");
        request.setAeTitle("XR_ROOM1");

        assertThrows(IllegalStateException.class,
                () -> service.registerModality(tenant, "fac-1", request, "actor-1", "ADMIN"));
    }

    @Test
    void registerModality_persistsAndOmitsNetworkDetailsFromEvent() throws Exception {
        when(modalityRepository.findByTenantIdAndFacilityIdAndAeTitle(tenant, "fac-1", "CT_MAIN"))
                .thenReturn(Optional.empty());
        when(modalityRepository.save(any(ImagingModalityEntity.class))).thenAnswer(inv -> {
            ImagingModalityEntity e = inv.getArgument(0);
            e.setId(3L);
            return e;
        });

        ImagingModalityRequest request = new ImagingModalityRequest();
        request.setModalityType("ct");
        request.setName("Main CT");
        request.setAeTitle("CT_MAIN");
        request.setHost("10.0.0.15");
        request.setPort(104);
        request.setSupportsWorklist(true);

        ImagingModalityEntity saved = service.registerModality(tenant, "fac-1", request, "actor-1", "ADMIN");

        assertEquals("CT", saved.getModalityType());
        assertEquals("10.0.0.15", saved.getHost());
        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertEquals(FacilityImagingCapabilityService.EVENT_MODALITY_REGISTERED, outbox.getValue().getEventType());
        Map<?, ?> payload = new ObjectMapper().readValue(outbox.getValue().getPayload(), Map.class);
        assertFalse(payload.containsKey("host"), "network details must not leak onto the event bus");
        assertFalse(payload.containsKey("ae_title"), "network details must not leak onto the event bus");
    }

    @Test
    void readiness_unconfiguredFacilityIsHonest() {
        when(capabilityRepository.findByTenantIdAndFacilityId(tenant, "fac-x")).thenReturn(Optional.empty());
        Map<String, Object> readiness = service.readiness(tenant, "fac-x");
        assertEquals(false, readiness.get("configured"));
        assertEquals("NOT_CONFIGURED", readiness.get("readiness"));
    }

    @Test
    void readiness_gatewayModeWithoutModalitiesIsNotReady() {
        FacilityImagingCapabilityEntity cap = new FacilityImagingCapabilityEntity();
        cap.setTenantId(tenant);
        cap.setFacilityId("fac-1");
        cap.setDeploymentMode("GATEWAY_STORE_FORWARD");
        cap.setLocalGatewayAvailable(true);
        cap.setOperationalStatus("UNKNOWN");
        when(capabilityRepository.findByTenantIdAndFacilityId(tenant, "fac-1")).thenReturn(Optional.of(cap));
        when(modalityRepository.findByTenantIdAndFacilityIdAndActiveTrueOrderByNameAsc(tenant, "fac-1"))
                .thenReturn(List.of());

        Map<String, Object> readiness = service.readiness(tenant, "fac-1");
        assertNotEquals("READY", readiness.get("readiness"));
    }

    @Test
    void readiness_fullyConfiguredPacsFacilityIsReady() {
        FacilityImagingCapabilityEntity cap = new FacilityImagingCapabilityEntity();
        cap.setTenantId(tenant);
        cap.setFacilityId("central-1");
        cap.setDeploymentMode("PACS_VNA");
        cap.setPacsVnaAvailable(true);
        cap.setViewerAvailable(true);
        cap.setReportingAvailable(true);
        cap.setOperationalStatus("OPERATIONAL");
        when(capabilityRepository.findByTenantIdAndFacilityId(tenant, "central-1")).thenReturn(Optional.of(cap));
        ImagingModalityEntity ct = new ImagingModalityEntity();
        ct.setId(1L);
        when(modalityRepository.findByTenantIdAndFacilityIdAndActiveTrueOrderByNameAsc(tenant, "central-1"))
                .thenReturn(List.of(ct));

        Map<String, Object> readiness = service.readiness(tenant, "central-1");
        assertEquals("READY", readiness.get("readiness"));
    }

    @Test
    void readiness_noneModeReportsNoImaging() {
        FacilityImagingCapabilityEntity cap = new FacilityImagingCapabilityEntity();
        cap.setTenantId(tenant);
        cap.setFacilityId("clinic-1");
        cap.setDeploymentMode("NONE");
        when(capabilityRepository.findByTenantIdAndFacilityId(tenant, "clinic-1")).thenReturn(Optional.of(cap));

        Map<String, Object> readiness = service.readiness(tenant, "clinic-1");
        assertEquals("NO_IMAGING", readiness.get("readiness"));
    }
}
