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
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityIdentifierSystem;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityImportRowEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityContactRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityGeoRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityIdentifierRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityImportRunRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityImportRowRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityImportRunEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
    @Mock
    private FacilityImportRunRepository importRunRepository;
    @Mock
    private FacilityImportRowRepository importRowRepository;

    private FacilityMasterImportService service;
    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        service = new FacilityMasterImportService(
                facilityRepository,
                identifierRepository,
                contactRepository,
                geoRepository,
                importRunRepository,
                importRowRepository,
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

    @Test
    void missingFacilityCodeIsExcludedNotSynthesised() {
        var record = new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-nocode", null, "No Code Clinic", "Harare", "Harare",
                "CLINIC", "GOVERNMENT", "Urban", "Primary", "Open", 4,
                -17.8, 31.0, "+263771111111", "2024-07-23");
        when(identifierRepository.findBySystemAndValue(
                FacilityMasterImportService.MASTER_FACILITY_UID_SYSTEM, "mhf-nocode"))
                .thenReturn(Optional.empty());

        var response = service.importPack(new FacilityMasterImportDtos.FacilityMasterImportRequest(
                false, false, false, List.of(record)));

        // Product truth: never imported, never given a synthetic code.
        assertThat(response.recordsCreated()).isZero();
        assertThat(response.recordsSkipped()).isEqualTo(1);
        verify(facilityRepository, never()).save(any());
    }

    @Test
    void importedFacilityEntersPendingConfigurationNotActive() {
        var record = new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-new", "ZWNEW1", "New Clinic", "Harare", "Harare",
                "CLINIC", "GOVERNMENT", "Urban", "Primary", "Open", 4,
                -17.8, 31.0, "+263772222222", "2024-07-23");
        when(identifierRepository.findBySystemAndValue(
                FacilityMasterImportService.MASTER_FACILITY_UID_SYSTEM, "mhf-new"))
                .thenReturn(Optional.empty());
        when(facilityRepository.findByTenantIdAndFacilityCode(tenantId, "ZWNEW1")).thenReturn(Optional.empty());
        when(facilityRepository.save(any(FacilityEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(contactRepository.findByFacilityId(any())).thenReturn(List.of());
        when(geoRepository.findByFacilityId(any())).thenReturn(Optional.empty());

        ArgumentCaptor<FacilityEntity> captor = ArgumentCaptor.forClass(FacilityEntity.class);
        var response = service.importPack(new FacilityMasterImportDtos.FacilityMasterImportRequest(
                false, false, false, List.of(record)));

        assertThat(response.recordsCreated()).isEqualTo(1);
        verify(facilityRepository).save(captor.capture());
        assertThat(captor.getValue().getRegulatoryStatus())
                .isEqualTo(FacilityRegulatoryStatus.IMPORTED_PENDING_CONFIGURATION);
    }

    @Test
    void blankMasterValuesDoNotOverwriteExistingVerifiedData() {
        FacilityEntity existing = new FacilityEntity();
        existing.setId(77L);
        existing.setTenantId(tenantId);
        existing.setFacilityCode("ZW999");
        existing.setName("Verified Name");
        existing.setOwnership("GOVERNMENT");
        existing.setFacilityType("HOSPITAL");
        existing.setVersion(3);

        FacilityIdentifierEntity ident = new FacilityIdentifierEntity();
        ident.setFacility(existing);
        ident.setSystem(FacilityMasterImportService.MASTER_FACILITY_UID_SYSTEM);
        ident.setValue("mhf-existing");

        when(identifierRepository.findBySystemAndValue(
                FacilityMasterImportService.MASTER_FACILITY_UID_SYSTEM, "mhf-existing"))
                .thenReturn(Optional.of(ident));
        when(facilityRepository.save(any(FacilityEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // Acceptable-missing incoming row: blank ownership/type/status, no coordinates, no phone.
        var record = new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-existing", "ZW999", "Verified Name", "Harare", "Harare",
                "", "", "Urban", "Primary", "", 4, null, null, null, "2024-07-23");

        ArgumentCaptor<FacilityEntity> captor = ArgumentCaptor.forClass(FacilityEntity.class);
        service.importPack(new FacilityMasterImportDtos.FacilityMasterImportRequest(
                false, false, false, List.of(record)));

        verify(facilityRepository).save(captor.capture());
        FacilityEntity saved = captor.getValue();
        assertThat(saved.getOwnership()).isEqualTo("GOVERNMENT");
        assertThat(saved.getFacilityType()).isEqualTo("HOSPITAL");
    }

    @Test
    void facilityCodeStoredAsExternalNationalIdentifierNotAsInternalIdentity() {
        var record = new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-extid", "ZWEXT9", "External Id Clinic", "Harare", "Harare",
                "CLINIC", "GOVERNMENT", "Urban", "Primary", "Open", 4,
                -17.8, 31.0, null, "2024-07-23");
        when(identifierRepository.findBySystemAndValue(
                FacilityMasterImportService.MASTER_FACILITY_UID_SYSTEM, "mhf-extid"))
                .thenReturn(Optional.empty());
        when(facilityRepository.findByTenantIdAndFacilityCode(tenantId, "ZWEXT9")).thenReturn(Optional.empty());
        when(facilityRepository.save(any(FacilityEntity.class))).thenAnswer(inv -> {
            FacilityEntity f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(101L);
            }
            return f;
        });
        when(geoRepository.findByFacilityId(any())).thenReturn(Optional.empty());

        service.importPack(new FacilityMasterImportDtos.FacilityMasterImportRequest(
                false, false, false, List.of(record)));

        ArgumentCaptor<FacilityIdentifierEntity> captor = ArgumentCaptor.forClass(FacilityIdentifierEntity.class);
        verify(identifierRepository, atLeastOnce()).save(captor.capture());

        // Security truth: the public facility code is persisted as an EXTERNAL matching identifier,
        // never as the internal facility identity or an authority.
        var national = captor.getAllValues().stream()
                .filter(i -> FacilityIdentifierSystem.NATIONAL_FACILITY_CODE.equals(i.getSystem()))
                .findFirst();
        assertThat(national).isPresent();
        assertThat(national.get().getValue()).isEqualTo("ZWEXT9");
        assertThat(FacilityIdentifierSystem.isInternalIdentity(national.get().getSystem())).isFalse();
    }

    @Test
    void reimportReusesInternalIdentityAndDoesNotRegenerateItFromTheCode() {
        UUID stableUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        FacilityEntity existing = new FacilityEntity();
        existing.setId(42L);
        existing.setFacilityUuid(stableUuid);
        existing.setTenantId(tenantId);
        existing.setFacilityCode("ZW010125");
        existing.setName("Bangure Clinic");
        existing.setVersion(1);

        FacilityIdentifierEntity ident = new FacilityIdentifierEntity();
        ident.setFacility(existing);
        ident.setSystem(FacilityMasterImportService.MASTER_FACILITY_UID_SYSTEM);
        ident.setValue("mhf-test");

        when(identifierRepository.findBySystemAndValue(
                FacilityMasterImportService.MASTER_FACILITY_UID_SYSTEM, "mhf-test"))
                .thenReturn(Optional.of(ident));
        when(facilityRepository.save(any(FacilityEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(contactRepository.findByFacilityId(42L)).thenReturn(List.of());
        when(geoRepository.findByFacilityId(42L)).thenReturn(Optional.empty());

        ArgumentCaptor<FacilityEntity> captor = ArgumentCaptor.forClass(FacilityEntity.class);
        service.importPack(new FacilityMasterImportDtos.FacilityMasterImportRequest(
                false, false, false, List.of(sampleRecord())));

        verify(facilityRepository).save(captor.capture());
        FacilityEntity saved = captor.getValue();
        // Internal identity is matched by the correlation key and reused as-is — never regenerated,
        // and never derived from the public facility code.
        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(saved.getFacilityUuid()).isEqualTo(stableUuid);
    }

    @Test
    void duplicateFacilityNameWithinBatchIsExcludedForReviewNotImported() {
        // Two distinct rows (different uid + code) that share a facility name.
        var a = new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-a", "ZWA1", "Shared Name Clinic", "Harare", "Harare",
                "CLINIC", "GOVERNMENT", "Urban", "Primary", "Open", 4,
                -17.8, 31.0, null, "2024-07-23");
        var b = new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-b", "ZWB2", "shared name clinic", "Manicaland", "Buhera",
                "CLINIC", "GOVERNMENT", "Rural", "Primary", "Open", 4,
                -19.5, 31.7, null, "2024-07-23");
        when(identifierRepository.findBySystemAndValue(
                FacilityMasterImportService.MASTER_FACILITY_UID_SYSTEM, "mhf-a")).thenReturn(Optional.empty());
        when(identifierRepository.findBySystemAndValue(
                FacilityMasterImportService.MASTER_FACILITY_UID_SYSTEM, "mhf-b")).thenReturn(Optional.empty());

        var response = service.importPack(new FacilityMasterImportDtos.FacilityMasterImportRequest(
                false, false, false, List.of(a, b)));

        // Both excluded for review — never created, never merged.
        assertThat(response.recordsCreated()).isZero();
        assertThat(response.recordsSkipped()).isEqualTo(2);
        verify(facilityRepository, never()).save(any());
    }

    @Test
    void importRunAuditRowIsPersistedWithBatchCounts() {
        var record = new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-audit", "ZWAUD1", "Audit Clinic", "Harare", "Harare",
                "CLINIC", "GOVERNMENT", "Urban", "Primary", "Open", 4,
                -17.8, 31.0, null, "2024-07-23");
        when(identifierRepository.findBySystemAndValue(
                FacilityMasterImportService.MASTER_FACILITY_UID_SYSTEM, "mhf-audit")).thenReturn(Optional.empty());
        when(facilityRepository.findByTenantIdAndFacilityCode(tenantId, "ZWAUD1")).thenReturn(Optional.empty());
        when(facilityRepository.save(any(FacilityEntity.class))).thenAnswer(inv -> {
            FacilityEntity f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(201L);
            }
            return f;
        });
        when(geoRepository.findByFacilityId(any())).thenReturn(Optional.empty());

        service.importPack(new FacilityMasterImportDtos.FacilityMasterImportRequest(
                false, false, false, List.of(record)));

        ArgumentCaptor<FacilityImportRunEntity> captor = ArgumentCaptor.forClass(FacilityImportRunEntity.class);
        verify(importRunRepository).save(captor.capture());
        FacilityImportRunEntity run = captor.getValue();
        assertThat(run.getTenantId()).isEqualTo(tenantId);
        assertThat(run.isDryRun()).isFalse();
        assertThat(run.getRecordsTotal()).isEqualTo(1);
        assertThat(run.getRecordsCreated()).isEqualTo(1);
        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        assertThat(run.getCompletedAt()).isNotNull();
    }

    @Test
    void persistsRowLevelOutcomesLinkedToRunWithAcceptableMissingAndRaw() {
        // Imported-but-incomplete (missing ownership + coordinates), a missing-code exclusion, and a
        // duplicate-name pair.
        var imported = new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-imp", "ZWIMP1", "Imported Clinic", "Harare", "Harare",
                "CLINIC", "", "Urban", "Primary", "Open", 4, null, null, null, "2024-07-23",
                java.util.Map.of("facility_name", "Imported Clinic RAW"));
        var noCode = new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-nc", null, "No Code Clinic", "Harare", "Harare",
                "CLINIC", "GOVERNMENT", "Urban", "Primary", "Open", 4, -17.8, 31.0, null, "2024-07-23");
        var dupA = new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-da", "ZWDA", "Twin Clinic", "Harare", "Harare",
                "CLINIC", "GOVERNMENT", "Urban", "Primary", "Open", 4, -17.8, 31.0, null, "2024-07-23");
        var dupB = new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-db", "ZWDB", "Twin Clinic", "Manicaland", "Buhera",
                "CLINIC", "GOVERNMENT", "Rural", "Primary", "Open", 4, -19.5, 31.7, null, "2024-07-23");

        when(identifierRepository.findBySystemAndValue(any(), any())).thenReturn(Optional.empty());
        when(facilityRepository.findByTenantIdAndFacilityCode(any(), any())).thenReturn(Optional.empty());
        when(facilityRepository.save(any(FacilityEntity.class))).thenAnswer(inv -> {
            FacilityEntity f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(900L);
            }
            return f;
        });
        FacilityImportRunEntity savedRun = new FacilityImportRunEntity();
        savedRun.setId(500L);
        when(importRunRepository.save(any(FacilityImportRunEntity.class))).thenReturn(savedRun);

        service.importPack(new FacilityMasterImportDtos.FacilityMasterImportRequest(
                false, false, false, List.of(imported, noCode, dupA, dupB)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FacilityImportRowEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(importRowRepository).saveAll(captor.capture());
        List<FacilityImportRowEntity> rows = captor.getValue();
        assertThat(rows).hasSize(4);
        assertThat(rows).allSatisfy(r -> assertThat(r.getImportRunId()).isEqualTo(500L));

        var importedRow = rows.stream().filter(r -> "ZWIMP1".equals(r.getFacilityCode())).findFirst().orElseThrow();
        assertThat(importedRow.getOutcome()).isEqualTo(FacilityImportRowEntity.IMPORTED);
        assertThat(importedRow.getResultFacilityId()).isEqualTo(900L);
        assertThat(importedRow.isHasAcceptableMissing()).isTrue();
        assertThat(importedRow.getAcceptableMissing().get("missing_ownership")).isEqualTo(true);
        assertThat(importedRow.getAcceptableMissing().get("missing_latitude")).isEqualTo(true);
        assertThat(importedRow.getRawValues()).containsEntry("facility_name", "Imported Clinic RAW");

        var noCodeRow = rows.stream().filter(r -> r.getFacilityName().equals("No Code Clinic")).findFirst().orElseThrow();
        assertThat(noCodeRow.getOutcome()).isEqualTo(FacilityImportRowEntity.EXCLUDED_MISSING_FACILITY_CODE);
        assertThat(noCodeRow.getResultFacilityId()).isNull();

        var dupRows = rows.stream().filter(r -> "Twin Clinic".equals(r.getFacilityName())).toList();
        assertThat(dupRows).hasSize(2);
        assertThat(dupRows).allSatisfy(r -> {
            assertThat(r.getOutcome()).isEqualTo(FacilityImportRowEntity.EXCLUDED_DUPLICATE_FACILITY_NAME);
            assertThat(r.getDuplicateType()).isEqualTo(FacilityImportRowEntity.DUP_TYPE_NAME);
            assertThat(r.getDuplicateGroupKey()).isEqualTo("NAME:twin clinic");
        });
    }

    @Test
    void qualitySummaryBreaksDownExclusionsFromRealOutcomes() {
        // One clean create, two duplicate-name rows (excluded), one missing-code row (excluded).
        var clean = new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-clean", "ZWC1", "Clean Clinic", "Harare", "Harare",
                "CLINIC", "GOVERNMENT", "Urban", "Primary", "Open", 4,
                -17.8, 31.0, null, "2024-07-23");
        var dupA = new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-dupa", "ZWD1", "Dup Name", "Harare", "Harare",
                "CLINIC", "GOVERNMENT", "Urban", "Primary", "Open", 4, -17.8, 31.0, null, "2024-07-23");
        var dupB = new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-dupb", "ZWD2", "Dup Name", "Harare", "Harare",
                "CLINIC", "GOVERNMENT", "Urban", "Primary", "Open", 4, -17.8, 31.0, null, "2024-07-23");
        var noCode = new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                "mhf-nocode", null, "No Code Clinic", "Harare", "Harare",
                "CLINIC", "GOVERNMENT", "Urban", "Primary", "Open", 4, -17.8, 31.0, null, "2024-07-23");
        when(identifierRepository.findBySystemAndValue(any(), any())).thenReturn(Optional.empty());
        when(facilityRepository.findByTenantIdAndFacilityCode(any(), any())).thenReturn(Optional.empty());
        when(facilityRepository.save(any(FacilityEntity.class))).thenAnswer(inv -> {
            FacilityEntity f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(301L);
            }
            return f;
        });
        when(geoRepository.findByFacilityId(any())).thenReturn(Optional.empty());

        service.importPack(new FacilityMasterImportDtos.FacilityMasterImportRequest(
                false, false, false, List.of(clean, dupA, dupB, noCode)));

        ArgumentCaptor<FacilityImportRunEntity> captor = ArgumentCaptor.forClass(FacilityImportRunEntity.class);
        verify(importRunRepository).save(captor.capture());
        var q = captor.getValue().getQualityReport();
        assertThat(q.get("records_total")).isEqualTo(4);
        assertThat(((Number) q.get("records_imported")).longValue()).isEqualTo(1L);
        assertThat(((Number) q.get("records_duplicate_facility_name")).longValue()).isEqualTo(2L);
        assertThat(((Number) q.get("records_missing_facility_code")).longValue()).isEqualTo(1L);
        assertThat(((Number) q.get("records_excluded_total")).longValue()).isEqualTo(3L);
    }

    @Test
    void listRunsMapsPersistedRunToViewWithBreakdown() {
        FacilityImportRunEntity run = new FacilityImportRunEntity();
        run.setId(7L);
        run.setTenantId(tenantId);
        run.setPackId(FacilityMasterImportService.PACK_ID);
        run.setDryRun(false);
        run.setRecordsTotal(4);
        run.setRecordsCreated(1);
        run.setRecordsSkipped(3);
        run.setStatus("COMPLETED");
        run.setInitiatedBy("operator-bootstrap");
        run.setQualityReport(java.util.Map.of(
                "source_label", FacilityMasterImportService.SOURCE_LABEL,
                "records_imported", 1,
                "records_missing_facility_code", 1,
                "records_duplicate_facility_name", 2,
                "records_excluded_total", 3));
        when(importRunRepository.findByTenantIdOrderByStartedAtDesc(tenantId)).thenReturn(List.of(run));

        var views = service.listRuns();

        assertThat(views).hasSize(1);
        var v = views.get(0);
        assertThat(v.runId()).isEqualTo(7L);
        assertThat(v.sourceLabel()).isEqualTo(FacilityMasterImportService.SOURCE_LABEL);
        assertThat(v.sourceFileName()).isNull(); // not captured per-run yet
        assertThat(v.totals().recordsImported()).isEqualTo(1L);
        assertThat(v.totals().duplicateFacilityName()).isEqualTo(2L);
        assertThat(v.totals().excludedTotal()).isEqualTo(3L);
    }

    @Test
    void getRunIsTenantScoped() {
        FacilityImportRunEntity otherTenantRun = new FacilityImportRunEntity();
        otherTenantRun.setId(9L);
        otherTenantRun.setTenantId(UUID.fromString("99999999-9999-9999-9999-999999999999"));
        when(importRunRepository.findById(9L)).thenReturn(Optional.of(otherTenantRun));

        assertThat(service.getRun(9L)).isEmpty();
    }

    @Test
    void searchRowsMapsPageToPagedRows() {
        FacilityImportRowEntity e = new FacilityImportRowEntity();
        e.setId(1L);
        e.setImportRunId(500L);
        e.setFacilityCode("ZW1");
        e.setFacilityName("Row Clinic");
        e.setOutcome(FacilityImportRowEntity.IMPORTED);
        when(importRowRepository.search(eq(500L), eq("IMPORTED"), any(), any(), any(), any(), any(), any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e), PageRequest.of(0, 50), 1));

        var paged = service.searchRows(500L, "IMPORTED", null, null, null, null, null, null, 0, 50);

        assertThat(paged.totalElements()).isEqualTo(1);
        assertThat(paged.content()).hasSize(1);
        assertThat(paged.content().get(0).facilityCode()).isEqualTo("ZW1");
        assertThat(paged.content().get(0).outcome()).isEqualTo("IMPORTED");
    }

    @Test
    void reviewBucketsAggregatesPersistedCounts() {
        when(importRowRepository.countByOutcome(500L)).thenReturn(List.of(
                new Object[]{FacilityImportRowEntity.IMPORTED, 3L},
                new Object[]{FacilityImportRowEntity.EXCLUDED_MISSING_FACILITY_CODE, 1L},
                new Object[]{FacilityImportRowEntity.EXCLUDED_DUPLICATE_FACILITY_NAME, 2L}));
        when(importRowRepository.countByImportRunIdAndHasAcceptableMissingTrue(500L)).thenReturn(4L);
        when(importRowRepository.search(any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class))).thenReturn(Page.empty());

        var buckets = service.reviewBuckets(500L);

        assertThat(buckets.importedRows().count()).isEqualTo(3);
        assertThat(buckets.missingFacilityCode().count()).isEqualTo(1);
        assertThat(buckets.duplicateFacilityNames().count()).isEqualTo(2);
        assertThat(buckets.acceptableMissingFields().count()).isEqualTo(4);
        assertThat(buckets.duplicateFacilityCodes().count()).isZero();
        assertThat(buckets.failedRows().count()).isZero();
    }

    @Test
    void duplicateGroupsGroupsRowsByKey() {
        FacilityImportRowEntity a = new FacilityImportRowEntity();
        a.setImportRunId(500L);
        a.setFacilityCode("ZWDA");
        a.setFacilityName("Twin Clinic");
        a.setOutcome(FacilityImportRowEntity.EXCLUDED_DUPLICATE_FACILITY_NAME);
        a.setDuplicateType(FacilityImportRowEntity.DUP_TYPE_NAME);
        a.setDuplicateGroupKey("NAME:twin clinic");
        FacilityImportRowEntity b = new FacilityImportRowEntity();
        b.setImportRunId(500L);
        b.setFacilityCode("ZWDB");
        b.setFacilityName("Twin Clinic");
        b.setOutcome(FacilityImportRowEntity.EXCLUDED_DUPLICATE_FACILITY_NAME);
        b.setDuplicateType(FacilityImportRowEntity.DUP_TYPE_NAME);
        b.setDuplicateGroupKey("NAME:twin clinic");
        when(importRowRepository.findByImportRunIdAndDuplicateTypeOrderByDuplicateGroupKeyAscFacilityCodeAsc(
                500L, FacilityImportRowEntity.DUP_TYPE_NAME)).thenReturn(List.of(a, b));

        var resp = service.duplicateGroups(500L, FacilityImportRowEntity.DUP_TYPE_NAME);

        assertThat(resp.groupCount()).isEqualTo(1);
        assertThat(resp.groups().get(0).size()).isEqualTo(2);
        assertThat(resp.groups().get(0).duplicateGroupKey()).isEqualTo("NAME:twin clinic");
    }

    @Test
    void loadsCleanCsvDerivingStableCorrelationKeyNotFromCode() throws Exception {
        String csv = String.join("\n",
                "source_row,facility_code,facility_name,province,district,latitude,longitude,"
                        + "facility_type_raw,facility_type_canonical,ownership_raw,ownership_canonical,"
                        + "location_raw,location_canonical,level_raw,level_canonical,contact_raw,status_raw,"
                        + "status_canonical,bed_capacity,date_opened,date_closed,comments,acceptable_missing_fields,"
                        + "frontend_missing_visible,geospatial_readiness,facility_setup_state,source_label",
                "2,ZW010125,Bangure Clinic,Manicaland,Buhera,-19.534847,31.759614,RDC,CLINIC,Rural Council,"
                        + "RURAL_DISTRICT_COUNCIL,Rural,RURAL,Primary,PRIMARY,776673131,Open,ACTIVE_OR_OPERATIONAL,"
                        + "6,,,,,NO,READY_WITH_COORDINATES,IMPORTED_PENDING_CONFIGURATION,MASTER_HEALTH_FACILITY_2024_07_23",
                "5,ZW020200,\"Mba, Rural Hospital\",Harare,Harare,,,Hospital,HOSPITAL,Govt,GOVERNMENT,Urban,URBAN,"
                        + "Secondary,SECONDARY,,,,,,,,,YES,MISSING_COORDINATES,IMPORTED_PENDING_CONFIGURATION,"
                        + "MASTER_HEALTH_FACILITY_2024_07_23");
        Path tmp = Files.createTempFile("clean_facilities", ".csv");
        Files.writeString(tmp, csv);

        var records = service.loadPackFromCsv(tmp);

        assertThat(records).hasSize(2);
        var first = records.get(0);
        assertThat(first.facilityCode()).isEqualTo("ZW010125");
        assertThat(first.facilityName()).isEqualTo("Bangure Clinic");
        assertThat(first.facilityType()).isEqualTo("CLINIC");
        assertThat(first.ownership()).isEqualTo("RURAL_DISTRICT_COUNCIL");
        assertThat(first.latitude()).isEqualTo(-19.534847);
        assertThat(first.status()).isEqualTo("Open");
        assertThat(first.bedCapacity()).isEqualTo(6);
        assertThat(first.sourceDatasetDate()).isEqualTo(FacilityMasterImportService.SOURCE_LABEL_DATE);
        // Correlation key derived from provenance (source_row), never from the public facility code.
        assertThat(first.facilityUid())
                .isEqualTo("MHF-" + FacilityMasterImportService.SOURCE_LABEL + "-row2");
        assertThat(first.facilityUid()).doesNotContain("ZW010125");

        // Quoted name with an embedded comma parses intact; missing coordinates/status stay null (not faked).
        var second = records.get(1);
        assertThat(second.facilityName()).isEqualTo("Mba, Rural Hospital");
        assertThat(second.latitude()).isNull();
        assertThat(second.longitude()).isNull();
        assertThat(second.status()).isNull();
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
