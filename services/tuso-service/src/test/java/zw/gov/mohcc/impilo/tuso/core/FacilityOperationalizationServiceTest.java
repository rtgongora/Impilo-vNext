package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ServicePointEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityCapabilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityReadinessRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityOperationalizationServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private WorkspaceService workspaceService;
    @Mock private ServicePointService servicePointService;
    @Mock private FacilityCapabilityRepository capabilityRepository;
    @Mock private FacilityReadinessRepository readinessRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private FacilityOperationalizationService service;
    private final UUID tenantId = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new FacilityOperationalizationService(
                facilityRepository, workspaceService, servicePointService,
                capabilityRepository, readinessRepository, transactionManager);
        TrustContextHolder.set(new TrustContext(
                tenantId, "ops-tester", "PLATFORM", "OPERATIONS",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private FacilityEntity facility(long id, String type) {
        FacilityEntity f = new FacilityEntity();
        f.setId(id);
        f.setTenantId(tenantId);
        f.setName("Facility " + id);
        f.setFacilityType(type);
        return f;
    }

    @Test
    void dryRunReportsCandidatesWithoutProvisioning() {
        when(facilityRepository.findWithoutActiveWorkspace(eq(tenantId), any(), any()))
                .thenReturn(new PageImpl<>(List.of(facility(10L, "CLINIC")), PageRequest.of(0, 100), 1773));

        var result = service.operationalizeDefaults(null, 100, true);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.remaining()).isEqualTo(1773);
        verify(workspaceService, never()).createWorkspace(anyLong(), any());
    }

    @Test
    void provisionsWorkspaceServicePointCapabilitiesAndReadiness() {
        FacilityEntity clinic = facility(10L, "RURAL_HEALTH_CENTRE");
        when(facilityRepository.findWithoutActiveWorkspace(eq(tenantId), any(), any()))
                .thenReturn(new PageImpl<>(List.of(clinic), PageRequest.of(0, 100), 1));
        when(facilityRepository.findById(10L)).thenReturn(Optional.of(clinic));
        when(servicePointService.list(10L)).thenReturn(List.of());
        when(capabilityRepository.findByFacilityIdAndActiveTrue(10L)).thenReturn(List.of());
        when(readinessRepository.findByFacilityId(10L)).thenReturn(Optional.empty());

        var result = service.operationalizeDefaults(null, 100, false);

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.workspacesCreated()).isEqualTo(1);
        assertThat(result.servicePointsCreated()).isEqualTo(1);
        // HAR S3 — OPD only. A rural health centre is not evidence of a maternity service: the
        // inference used to hold for RURAL_HEALTH_CENTRE/CLINIC and was removed, because "clinic"
        // in the HPA register covers dental, optometry and radiology practices, and a wrong
        // maternity claim is a promise a woman in labour would travel on.
        assertThat(result.capabilitiesCreated()).isEqualTo(1);
        assertThat(result.readinessCreated()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(workspaceService).createWorkspace(eq(10L), any());
    }

    @Test
    void skipsServicePointCapabilityReadinessWhenAlreadyPresent() {
        FacilityEntity hospital = facility(20L, "DISTRICT_HOSPITAL");
        when(facilityRepository.findWithoutActiveWorkspace(eq(tenantId), any(), any()))
                .thenReturn(new PageImpl<>(List.of(hospital), PageRequest.of(0, 100), 1));
        when(facilityRepository.findById(20L)).thenReturn(Optional.of(hospital));
        when(servicePointService.list(20L)).thenReturn(List.of(new ServicePointEntity()));
        when(capabilityRepository.findByFacilityIdAndActiveTrue(20L))
                .thenReturn(List.of(new zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCapabilityEntity()));
        when(readinessRepository.findByFacilityId(20L))
                .thenReturn(Optional.of(new zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityReadinessEntity()));

        var result = service.operationalizeDefaults(null, 100, false);

        assertThat(result.workspacesCreated()).isEqualTo(1); // candidacy = zero workspaces
        assertThat(result.servicePointsCreated()).isZero();
        assertThat(result.capabilitiesCreated()).isZero();
        assertThat(result.readinessCreated()).isZero();
        verify(servicePointService, never()).create(any(), anyLong(), any());
    }

    @Test
    void oneFailureDoesNotAbortTheBatch() {
        FacilityEntity bad = facility(30L, "CLINIC");
        FacilityEntity good = facility(31L, "CLINIC");
        when(facilityRepository.findWithoutActiveWorkspace(eq(tenantId), any(), any()))
                .thenReturn(new PageImpl<>(List.of(bad, good), PageRequest.of(0, 100), 2));
        when(facilityRepository.findById(30L)).thenReturn(Optional.empty()); // provokes failure
        when(facilityRepository.findById(31L)).thenReturn(Optional.of(good));
        when(servicePointService.list(31L)).thenReturn(List.of());
        when(capabilityRepository.findByFacilityIdAndActiveTrue(31L)).thenReturn(List.of());
        when(readinessRepository.findByFacilityId(31L)).thenReturn(Optional.empty());

        var result = service.operationalizeDefaults(null, 100, false);

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failedFacilityIds()).containsExactly(30L);
    }

    @Test
    void workspaceTypeAndCapabilityDerivationAreTypeAware() {
        assertThat(FacilityOperationalizationService.defaultWorkspaceType("PHARMACY")).isEqualTo("PHARMACY");
        assertThat(FacilityOperationalizationService.defaultWorkspaceType("DISTRICT_HOSPITAL")).isEqualTo("OPD");
        assertThat(FacilityOperationalizationService.defaultWorkspaceType(null)).isEqualTo("OPD");
        assertThat(FacilityOperationalizationService.defaultCapabilities("CENTRAL_HOSPITAL"))
                .extracting(c -> c[0]).containsExactly("OPD", "IPD", "MATERNITY");
        assertThat(FacilityOperationalizationService.defaultCapabilities("PRIVATE"))
                .extracting(c -> c[0]).containsExactly("OPD");
    }
}
