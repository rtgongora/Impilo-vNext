package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityMasterImportDtos;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityIdentifierEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityContactRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityGeoRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityIdentifierRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityMasterImportServiceTest {

    @Mock
    private FacilityRepository facilityRepository;
    @Mock
    private FacilityIdentifierRepository identifierRepository;
    @Mock
    private FacilityContactRepository contactRepository;
    @Mock
    private FacilityGeoRepository geoRepository;

    private FacilityMasterImportService service;
    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        service = new FacilityMasterImportService(
                facilityRepository,
                identifierRepository,
                contactRepository,
                geoRepository,
                new ObjectMapper());
        TrustContextHolder.set(new TrustContext(
                tenantId,
                "tester",
                "PROVIDER",
                "TREATMENT",
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                AccessMode.INTERNAL));
    }

    @Test
    void dryRunDoesNotPersistFacilities() {
        when(identifierRepository.findBySystemAndValue(FacilityMasterImportService.MASTER_FACILITY_UID_SYSTEM, "mhf-test"))
                .thenReturn(Optional.empty());
        when(facilityRepository.findByTenantIdAndFacilityCode(tenantId, "ZW010125"))
                .thenReturn(Optional.empty());

        var response = service.importPack(new FacilityMasterImportDtos.FacilityMasterImportRequest(
                true,
                false,
                false,
                List.of(sampleRecord())));

        assertThat(response.dryRun()).isTrue();
        assertThat(response.recordsCreated()).isEqualTo(1);
        verify(facilityRepository, never()).save(any());
    }

    @Test
    void repeatedImportUpdatesSameFacilityByUid() {
        FacilityEntity existing = new FacilityEntity();
        existing.setId(42L);
        existing.setTenantId(tenantId);
        existing.setFacilityCode("ZW010125");
        existing.setName("Old Name");
        existing.setVersion(1);

        FacilityIdentifierEntity ident = new FacilityIdentifierEntity();
        ident.setFacility(existing);
        ident.setSystem(FacilityMasterImportService.MASTER_FACILITY_UID_SYSTEM);
        ident.setValue("mhf-test");

        when(identifierRepository.findBySystemAndValue(FacilityMasterImportService.MASTER_FACILITY_UID_SYSTEM, "mhf-test"))
                .thenReturn(Optional.of(ident));
        when(facilityRepository.save(any(FacilityEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(identifierRepository.findBySystemAndValue(FacilityMasterImportService.MASTER_FACILITY_UID_SYSTEM, "mhf-test"))
                .thenReturn(Optional.of(ident));
        when(contactRepository.findByFacilityId(42L)).thenReturn(List.of());
        when(geoRepository.findByFacilityId(42L)).thenReturn(Optional.empty());

        var first = service.importPack(new FacilityMasterImportDtos.FacilityMasterImportRequest(
                false, false, false, List.of(sampleRecord())));
        var second = service.importPack(new FacilityMasterImportDtos.FacilityMasterImportRequest(
                false, false, false, List.of(sampleRecord())));

        assertThat(first.recordsUpdated()).isEqualTo(1);
        assertThat(second.recordsUpdated()).isEqualTo(1);
        verify(facilityRepository, times(2)).save(any(FacilityEntity.class));

        ArgumentCaptor<FacilityEntity> captor = ArgumentCaptor.forClass(FacilityEntity.class);
        verify(facilityRepository, times(2)).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Bangure Clinic");
    }

    private static FacilityMasterImportDtos.MasterFacilitySeedRecord sampleRecord() {
        return new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-test",
                "ZW010125",
                "Bangure Clinic",
                "Manicaland",
                "Buhera",
                "RDC",
                "Rural Council",
                "Rural",
                "Primary",
                "Open",
                6,
                -19.534847,
                31.759614,
                "+263776673131",
                "2024-07-23");
    }
}
