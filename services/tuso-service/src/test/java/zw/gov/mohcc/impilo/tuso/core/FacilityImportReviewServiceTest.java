package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityImportRowDtos;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityImportRowEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityImportRunEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityContactRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityGeoRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityIdentifierRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityImportRowRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityImportRunRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FacilityImportReviewServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilityIdentifierRepository identifierRepository;
    @Mock private FacilityContactRepository contactRepository;
    @Mock private FacilityGeoRepository geoRepository;
    @Mock private FacilityImportRunRepository importRunRepository;
    @Mock private FacilityImportRowRepository importRowRepository;
    @Mock private FacilitySourceLegitimacyService sourceLegitimacyService;

    private FacilityMasterImportService service;
    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        service = new FacilityMasterImportService(facilityRepository, identifierRepository,
                contactRepository, geoRepository, importRunRepository, importRowRepository,
                sourceLegitimacyService, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(tenantId, "reviewer-1", "ADMIN", "SYSTEM_ADMINISTRATION",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        FacilityImportRunEntity run = new FacilityImportRunEntity();
        run.setId(500L);
        run.setTenantId(tenantId);
        when(importRunRepository.findById(500L)).thenReturn(Optional.of(run));
        when(importRowRepository.save(any(FacilityImportRowEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    private FacilityImportRowEntity stagedRow(Long id, String outcome, String decisionStatus, String code) {
        FacilityImportRowEntity row = new FacilityImportRowEntity();
        row.setId(id);
        row.setImportRunId(500L);
        row.setSourceRow("42");
        row.setCorrelationKey("MHF-row42");
        row.setFacilityName("Row Clinic");
        row.setFacilityCode(code);
        row.setOutcome(outcome);
        row.setDecisionStatus(decisionStatus);
        row.setRawValues(Map.of("facility_name", "Row Clinic RAW"));
        when(importRowRepository.findByIdAndImportRunId(id, 500L)).thenReturn(Optional.of(row));
        return row;
    }

    @Test
    void supplyCodeSuccessMarksCorrectedReady() {
        stagedRow(1L, FacilityImportRowEntity.EXCLUDED_MISSING_FACILITY_CODE,
                FacilityImportRowEntity.DS_PENDING_REVIEW, null);
        when(facilityRepository.findByTenantIdAndFacilityCode(tenantId, "ZWX1")).thenReturn(Optional.empty());
        when(importRowRepository.findByImportRunIdAndFacilityCode(500L, "ZWX1")).thenReturn(List.of());

        var view = service.supplyCode(500L, 1L, "ZWX1", "supplied by registrar");

        assertThat(view.facilityCode()).isEqualTo("ZWX1");
        assertThat(view.decisionStatus()).isEqualTo(FacilityImportRowEntity.DS_CORRECTED_READY_FOR_IMPORT);
        assertThat(view.reviewHistory()).isNotEmpty();
        assertThat(view.reviewedBy()).isEqualTo("reviewer-1");
    }

    @Test
    void supplyCodeConflictLeavesRowInReview() {
        stagedRow(1L, FacilityImportRowEntity.EXCLUDED_MISSING_FACILITY_CODE,
                FacilityImportRowEntity.DS_PENDING_REVIEW, null);
        FacilityEntity existing = new FacilityEntity();
        existing.setId(10L);
        existing.setTenantId(tenantId);
        when(facilityRepository.findByTenantIdAndFacilityCode(tenantId, "DUP1")).thenReturn(Optional.of(existing));

        var view = service.supplyCode(500L, 1L, "DUP1", null);

        assertThat(view.decisionStatus()).isEqualTo(FacilityImportRowEntity.DS_RESOLUTION_CONFLICT);
        assertThat(view.conflictReason()).contains("already assigned to facility 10");
    }

    @Test
    void supplyBlankCodeIsRejected() {
        stagedRow(1L, FacilityImportRowEntity.EXCLUDED_MISSING_FACILITY_CODE,
                FacilityImportRowEntity.DS_PENDING_REVIEW, null);
        assertThatThrownBy(() -> service.supplyCode(500L, 1L, "   ", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectAndSkipSetTerminalStates() {
        stagedRow(1L, FacilityImportRowEntity.EXCLUDED_DUPLICATE_FACILITY_CODE,
                FacilityImportRowEntity.DS_PENDING_REVIEW, "ZWD");
        var rejected = service.rejectRow(500L, 1L, "bad row");
        assertThat(rejected.decisionStatus()).isEqualTo(FacilityImportRowEntity.DS_REJECTED);

        stagedRow(2L, FacilityImportRowEntity.EXCLUDED_DUPLICATE_FACILITY_NAME,
                FacilityImportRowEntity.DS_PENDING_REVIEW, "ZWE");
        var skipped = service.skipRow(500L, 2L, "not a facility");
        assertThat(skipped.decisionStatus()).isEqualTo(FacilityImportRowEntity.DS_SKIPPED);
    }

    @Test
    void matchExistingSetsMatchedFacility() {
        stagedRow(1L, FacilityImportRowEntity.EXCLUDED_DUPLICATE_FACILITY_NAME,
                FacilityImportRowEntity.DS_PENDING_REVIEW, null);
        FacilityEntity target = new FacilityEntity();
        target.setId(77L);
        target.setTenantId(tenantId);
        when(facilityRepository.findById(77L)).thenReturn(Optional.of(target));

        var view = service.matchExisting(500L, 1L, 77L, "same site");

        assertThat(view.matchedFacilityId()).isEqualTo(77L);
        assertThat(view.decisionStatus()).isEqualTo(FacilityImportRowEntity.DS_MATCHED_EXISTING_FACILITY);
    }

    @Test
    void resolveDistinctRequiresReason() {
        stagedRow(1L, FacilityImportRowEntity.EXCLUDED_DUPLICATE_FACILITY_NAME,
                FacilityImportRowEntity.DS_PENDING_REVIEW, "ZWN");
        assertThatThrownBy(() -> service.resolveDistinct(500L, 1L, "  "))
                .isInstanceOf(ResponseStatusException.class);

        var view = service.resolveDistinct(500L, 1L, "genuinely separate clinic");
        assertThat(view.decisionStatus()).isEqualTo(FacilityImportRowEntity.DS_RESOLVED_AS_DISTINCT_FACILITY);
    }

    @Test
    void approveBlockedWithoutCode() {
        stagedRow(1L, FacilityImportRowEntity.EXCLUDED_MISSING_FACILITY_CODE,
                FacilityImportRowEntity.DS_PENDING_REVIEW, null);
        assertThatThrownBy(() -> service.approveRow(500L, 1L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void approveCorrectedRowSucceeds() {
        stagedRow(1L, FacilityImportRowEntity.EXCLUDED_MISSING_FACILITY_CODE,
                FacilityImportRowEntity.DS_CORRECTED_READY_FOR_IMPORT, "ZWOK1");
        when(facilityRepository.findByTenantIdAndFacilityCode(tenantId, "ZWOK1")).thenReturn(Optional.empty());
        when(importRowRepository.findByImportRunIdAndFacilityCode(500L, "ZWOK1")).thenReturn(List.of());

        var view = service.approveRow(500L, 1L);
        assertThat(view.decisionStatus()).isEqualTo(FacilityImportRowEntity.DS_APPROVED_FOR_IMPORT);
    }

    @Test
    void applyApprovedCreatesFacilityAndPreservesRaw() {
        FacilityImportRowEntity row = stagedRow(1L, FacilityImportRowEntity.READY_FOR_IMPORT,
                FacilityImportRowEntity.DS_APPROVED_FOR_IMPORT, "ZWNEW");
        when(importRowRepository.findByImportRunIdAndDecisionStatus(500L,
                FacilityImportRowEntity.DS_APPROVED_FOR_IMPORT)).thenReturn(List.of(row));
        when(identifierRepository.findBySystemAndValue(any(), any())).thenReturn(Optional.empty());
        when(facilityRepository.findByTenantIdAndFacilityCode(any(), any())).thenReturn(Optional.empty());
        when(facilityRepository.save(any(FacilityEntity.class))).thenAnswer(i -> {
            FacilityEntity f = i.getArgument(0);
            if (f.getId() == null) f.setId(900L);
            return f;
        });

        var resp = service.applyApproved(500L, null);

        assertThat(resp.applied()).isEqualTo(1);
        assertThat(row.getResultFacilityId()).isEqualTo(900L);
        assertThat(row.getDecisionStatus()).isEqualTo(FacilityImportRowEntity.DS_IMPORTED);
        // Raw source values untouched by the apply.
        assertThat(row.getRawValues()).containsEntry("facility_name", "Row Clinic RAW");
    }

    @Test
    void applyApprovedUpdatesMatchedFacilityNotCreate() {
        FacilityImportRowEntity row = stagedRow(1L, FacilityImportRowEntity.EXCLUDED_DUPLICATE_FACILITY_NAME,
                FacilityImportRowEntity.DS_APPROVED_FOR_IMPORT, "ZWMATCH");
        row.setMatchedFacilityId(55L);
        FacilityEntity existing = new FacilityEntity();
        existing.setId(55L);
        existing.setTenantId(tenantId);
        existing.setVersion(2);
        when(importRowRepository.findByImportRunIdAndDecisionStatus(500L,
                FacilityImportRowEntity.DS_APPROVED_FOR_IMPORT)).thenReturn(List.of(row));
        when(facilityRepository.findById(55L)).thenReturn(Optional.of(existing));
        when(identifierRepository.findBySystemAndValue(any(), any())).thenReturn(Optional.empty());
        when(facilityRepository.save(any(FacilityEntity.class))).thenAnswer(i -> i.getArgument(0));

        var resp = service.applyApproved(500L, null);

        assertThat(resp.applied()).isEqualTo(1);
        assertThat(row.getResultFacilityId()).isEqualTo(55L);
    }

    @Test
    void applyApprovedIsIdempotentForAlreadyImportedRows() {
        FacilityImportRowEntity row = stagedRow(1L, FacilityImportRowEntity.IMPORTED,
                FacilityImportRowEntity.DS_APPROVED_FOR_IMPORT, "ZWDONE");
        row.setResultFacilityId(900L); // already applied
        when(importRowRepository.findByImportRunIdAndDecisionStatus(500L,
                FacilityImportRowEntity.DS_APPROVED_FOR_IMPORT)).thenReturn(List.of(row));

        var resp = service.applyApproved(500L, null);

        assertThat(resp.applied()).isZero();
        assertThat(resp.skipped()).isEqualTo(1);
        verify(facilityRepository, never()).save(any());
    }
}
