package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityIdentifierEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityIdentifierSystem;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityIdentifierRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityReadinessRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityUnitRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.PractitionerInChargeAssignmentRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ServicePointRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityProvenanceServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilityIdentifierRepository identifierRepository;
    @Mock private ServicePointRepository servicePointRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private FacilityUnitRepository unitRepository;
    @Mock private PractitionerInChargeAssignmentRepository picRepository;
    @Mock private FacilityReadinessRepository readinessRepository;

    private FacilityProvenanceService service() {
        return new FacilityProvenanceService(facilityRepository, identifierRepository,
                servicePointRepository, workspaceRepository, unitRepository, picRepository, readinessRepository);
    }

    @Test
    void importedPendingFacilitySurfacesMissingnessAndDownstreamPending() {
        FacilityEntity f = new FacilityEntity();
        f.setId(55L);
        f.setFacilityUuid(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        f.setFacilityCode("ZW010125");
        f.setName("Bangure Clinic");
        f.setRegulatoryStatus(FacilityRegulatoryStatus.IMPORTED_PENDING_CONFIGURATION);
        f.setOperationalStatus("MISSING_REQUIRES_CONFIRMATION");
        // Acceptable-missing: no coordinates, no type, no ownership.
        f.setLatitude(null);
        f.setLongitude(null);
        f.setMetadata(Map.of(
                "source_label", FacilityMasterImportService.SOURCE_LABEL,
                "facility_uid", "MHF-" + FacilityMasterImportService.SOURCE_LABEL + "-row42",
                "import_pack_id", FacilityMasterImportService.PACK_ID));

        FacilityIdentifierEntity national = new FacilityIdentifierEntity();
        national.setSystem(FacilityIdentifierSystem.NATIONAL_FACILITY_CODE);
        national.setValue("ZW010125");

        when(facilityRepository.findById(55L)).thenReturn(java.util.Optional.of(f));
        when(identifierRepository.findByFacilityIdAndActiveTrue(55L)).thenReturn(List.of(national));
        when(picRepository.findByFacilityIdOrderByStartDateDesc(anyLong())).thenReturn(List.of());
        when(servicePointRepository.countByFacilityIdAndActiveTrue(anyLong())).thenReturn(0L);
        when(workspaceRepository.findByFacilityIdAndActiveTrue(anyLong())).thenReturn(List.of());
        when(unitRepository.findByFacilityIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());
        when(readinessRepository.findByFacilityId(anyLong())).thenReturn(java.util.Optional.empty());

        var dto = service().buildProvenance(55L).orElseThrow();

        // Identity: internal id/uuid distinct from the public code, code labelled as admin code.
        assertThat(dto.identity().internalFacilityId()).isEqualTo(55L);
        assertThat(dto.identity().facilityUuid()).isEqualTo("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertThat(dto.identity().facilityCode()).isEqualTo("ZW010125");
        assertThat(dto.identity().facilityCodeLabel()).isEqualTo(FacilityProvenanceService.FACILITY_CODE_LABEL);
        assertThat(dto.identity().facilityCodeLabel()).doesNotContainIgnoringCase("facility id");
        assertThat(dto.identity().sourceLabel()).isEqualTo(FacilityMasterImportService.SOURCE_LABEL);
        assertThat(dto.identity().sourceRow()).isEqualTo("42");
        assertThat(dto.identity().importedFromMaster()).isTrue();
        assertThat(dto.identity().lifecycleState()).isEqualTo("IMPORTED_PENDING_CONFIGURATION");
        assertThat(dto.identity().externalIdentifiers())
                .anySatisfy(e -> assertThat(e.system()).isEqualTo(FacilityIdentifierSystem.NATIONAL_FACILITY_CODE));

        // Missingness kept visible (not faked).
        assertThat(dto.acceptableMissing().missingLatitude()).isTrue();
        assertThat(dto.acceptableMissing().missingFacilityType()).isTrue();
        assertThat(dto.acceptableMissing().missingOwnership()).isTrue();
        assertThat(dto.acceptableMissing().missingOperatingStatus()).isTrue();

        // Downstream config honestly pending; queues/workforce/stores owned downstream.
        assertThat(dto.downstreamMaterialisationStatus()).isEqualTo("PENDING_CONFIGURATION");
        assertThat(dto.checklist()).anySatisfy(c -> {
            if ("queues".equals(c.key())) {
                assertThat(c.status()).isEqualTo("PENDING_DOWNSTREAM");
                assertThat(c.owner()).isEqualTo("PCT");
            }
        });
        assertThat(dto.checklist()).anySatisfy(c -> {
            if ("service_points".equals(c.key())) {
                assertThat(c.status()).isEqualTo("MISSING");
                assertThat(c.owner()).isEqualTo("TUSO");
            }
        });
    }
}
