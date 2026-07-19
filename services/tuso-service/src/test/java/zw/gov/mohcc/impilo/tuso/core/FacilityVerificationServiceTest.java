package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityVerificationCaseEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityVerificationCaseRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FJ3/FJ4, D-L6: GPS plausibility is computed against registry truth at
 * submission; nothing auto-verifies (a case needs a verifier decision); a
 * decided case is immutable (repeat verification = new case); a decision
 * refreshes the trust dimensions.
 */
@ExtendWith(MockitoExtension.class)
class FacilityVerificationServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilityVerificationCaseRepository caseRepository;
    @Mock private FacilityTrustDimensionService trustDimensionService;
    @Mock private EventOutboxRepository outboxRepository;

    private FacilityVerificationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FacilityVerificationService(
                facilityRepository, caseRepository, trustDimensionService,
                outboxRepository, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(
                tenantId, "verifier-1", "REGISTRY_ADMIN", "VERIFICATION",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        FacilityEntity facility = new FacilityEntity();
        facility.setFacilityUuid(facilityUuid);
        facility.setTenantId(tenantId);
        facility.setLatitude(new BigDecimal("-17.8300000"));
        facility.setLongitude(new BigDecimal("31.0500000"));
        lenient().when(facilityRepository.findByFacilityUuid(facilityUuid))
                .thenReturn(Optional.of(facility));
        lenient().when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void openComputesGpsPlausibilityAgainstRegistryTruth() {
        // ~1.1km north of the registry point — recorded, and NOT plausible.
        FacilityVerificationCaseEntity opened = service.open(facilityUuid,
                new FacilityVerificationService.OpenCaseRequest(
                        "GUIDED_PHOTO", List.of("doc://photo/1"),
                        new BigDecimal("-17.8200000"), new BigDecimal("31.0500000"), null));

        assertThat(opened.getGpsDistanceMeters().doubleValue()).isBetween(1000.0, 1300.0);
        assertThat(opened.getStatus()).isEqualTo(FacilityVerificationCaseEntity.STATUS_OPEN);
        // Opening NEVER auto-verifies or touches trust dimensions.
        verify(trustDimensionService, never()).recompute(any(), any());
    }

    @Test
    void openRequiresEvidenceOrVideoSession() {
        assertThatThrownBy(() -> service.open(facilityUuid,
                new FacilityVerificationService.OpenCaseRequest(
                        "GUIDED_PHOTO", List.of(), null, null, null)))
                .isInstanceOf(ResponseStatusException.class);
        verify(caseRepository, never()).save(any());
    }

    @Test
    void decisionRefreshesTrustDimensions_andDecidedCaseIsImmutable() {
        FacilityVerificationCaseEntity open = new FacilityVerificationCaseEntity();
        open.setTenantId(tenantId);
        open.setCaseRef("FVC-1");
        open.setFacilityUuid(facilityUuid);
        open.setVerificationType("VIDEO_WALKTHROUGH");
        open.setRequestedBy("operator-1");
        open.setStatus(FacilityVerificationCaseEntity.STATUS_OPEN);
        when(caseRepository.findByIdAndTenantId(9L, tenantId)).thenReturn(Optional.of(open));

        FacilityVerificationCaseEntity decided = service.decide(9L, "verified", "existence + signage seen");

        assertThat(decided.getStatus()).isEqualTo(FacilityVerificationCaseEntity.STATUS_VERIFIED);
        assertThat(decided.getDecidedBy()).isEqualTo("verifier-1");
        verify(trustDimensionService).recompute(eq(tenantId), eq(facilityUuid));

        // The decided record is immutable — a second decision conflicts.
        assertThatThrownBy(() -> service.decide(9L, "FAILED", "changed my mind"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already decided");
    }

    @Test
    void haversineIsAccurateAtCityScale() {
        // Harare CBD → ~1 degree of longitude at this latitude ≈ 106 km
        double meters = FacilityVerificationService.haversineMeters(-17.83, 31.05, -17.83, 32.05);
        assertThat(meters).isBetween(105_000.0, 107_000.0);
        assertThat(FacilityVerificationService.haversineMeters(-17.83, 31.05, -17.83, 31.05)).isZero();
    }
}
