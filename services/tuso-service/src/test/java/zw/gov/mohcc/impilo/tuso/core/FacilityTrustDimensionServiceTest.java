package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityAdminAppointmentEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacySource;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySourceLegitimacyEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityTrustDimensionEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityAdminAppointmentRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityCapabilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySourceLegitimacyRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityTrustDimensionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * D-L8: nine dimensions, each from its own signal; unwired signals are UNKNOWN
 * (never inferred); legitimacy feeds LEGAL_IDENTITY only — a suspended permit
 * FAILS a dimension without deleting identity.
 */
@ExtendWith(MockitoExtension.class)
class FacilityTrustDimensionServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilitySourceLegitimacyRepository legitimacyRepository;
    @Mock private FacilityAdminAppointmentRepository appointmentRepository;
    @Mock private FacilityCapabilityRepository capabilityRepository;
    @Mock private FacilityTrustDimensionRepository dimensionRepository;

    private FacilityTrustDimensionService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityUuid = UUID.randomUUID();
    private FacilityEntity facility;

    @BeforeEach
    void setUp() {
        service = new FacilityTrustDimensionService(
                facilityRepository, legitimacyRepository, appointmentRepository,
                capabilityRepository, dimensionRepository);
        facility = new FacilityEntity();
        facility.setId(42L);
        facility.setFacilityUuid(facilityUuid);
        facility.setTenantId(tenantId);
        facility.setStatus("ACTIVE");
        facility.setOperationalStatus("OPERATIONAL");
        facility.setRegulatoryStatus(FacilityRegulatoryStatus.REGISTERED_ACTIVE);
        facility.setLatitude(new BigDecimal("-17.83"));
        facility.setLongitude(new BigDecimal("31.05"));
        when(facilityRepository.findByFacilityUuid(facilityUuid)).thenReturn(Optional.of(facility));
        lenient().when(dimensionRepository.findByTenantIdAndFacilityUuidAndDimension(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(dimensionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid))
                .thenReturn(List.of());
        lenient().when(capabilityRepository.findByFacilityIdAndActiveTrue(42L)).thenReturn(List.of());
    }

    private Map<String, FacilityTrustDimensionEntity> recompute() {
        return service.recompute(tenantId, facilityUuid).stream()
                .collect(Collectors.toMap(FacilityTrustDimensionEntity::getDimension, Function.identity()));
    }

    @Test
    void allNineDimensionsAreMaterialised_andUnwiredSignalsStayUnknown() {
        Map<String, FacilityTrustDimensionEntity> byDim = recompute();

        assertThat(byDim.keySet())
                .containsExactlyInAnyOrderElementsOf(FacilityTrustDimensionEntity.DIMENSIONS);
        // Unwired signals are HONESTLY unknown, never inferred.
        assertThat(byDim.get("EXISTENCE").getStatus())
                .isEqualTo(FacilityTrustDimensionEntity.STATUS_UNKNOWN);
        assertThat(byDim.get("COMPLIANCE").getStatus())
                .isEqualTo(FacilityTrustDimensionEntity.STATUS_UNKNOWN);
        // Coordinates without anchor verification = PARTIAL, not CONFIRMED.
        assertThat(byDim.get("GEOSPATIAL").getStatus())
                .isEqualTo(FacilityTrustDimensionEntity.STATUS_PARTIAL);
        assertThat(byDim.get("REGULATORY").getStatus())
                .isEqualTo(FacilityTrustDimensionEntity.STATUS_CONFIRMED);
        assertThat(byDim.get("OPERATIONAL").getStatus())
                .isEqualTo(FacilityTrustDimensionEntity.STATUS_CONFIRMED);
    }

    @Test
    void suspendedLegitimacyFailsLegalIdentityWithoutTouchingOtherDimensions() {
        FacilitySourceLegitimacyEntity denies = new FacilitySourceLegitimacyEntity();
        denies.setFacilityId(facilityUuid);
        denies.setSource(FacilityLegitimacySource.HPA_LEGAL);
        denies.setStatus(FacilityLegitimacyStatus.SUSPENDED);
        denies.setAsOf(Instant.now());
        denies.setAllowedOnPlatform(false);
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid))
                .thenReturn(List.of(denies));

        Map<String, FacilityTrustDimensionEntity> byDim = recompute();

        assertThat(byDim.get("LEGAL_IDENTITY").getStatus())
                .isEqualTo(FacilityTrustDimensionEntity.STATUS_FAILED);
        // A suspended permit does not delete identity: the rest still compute.
        assertThat(byDim.get("OPERATIONAL").getStatus())
                .isEqualTo(FacilityTrustDimensionEntity.STATUS_CONFIRMED);
    }

    @Test
    void activeAdministratorConfirmsAuthority() {
        when(appointmentRepository.existsByFacilityUuidAndApprovalState(
                facilityUuid, FacilityAdminAppointmentEntity.STATE_ACTIVE)).thenReturn(true);

        assertThat(recompute().get("AUTHORITY").getStatus())
                .isEqualTo(FacilityTrustDimensionEntity.STATUS_CONFIRMED);
    }

    @Test
    void closedFacilityFailsOperationalDimension() {
        facility.setOperationalStatus("CLOSED");

        assertThat(recompute().get("OPERATIONAL").getStatus())
                .isEqualTo(FacilityTrustDimensionEntity.STATUS_FAILED);
    }
}
