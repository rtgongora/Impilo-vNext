package zw.gov.mohcc.impilo.madi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.integration.NhumeIntegration;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodEmergencyRedistributionEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.BloodEmergencyRedistributionRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CentralBankServiceTest {

    @Mock
    private BloodEmergencyRedistributionRepository redistributionRepository;

    @Mock
    private MadiEventEmitter eventEmitter;

    @Mock
    private NhumeIntegration nhumeIntegration;

    private CentralBankService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new CentralBankService(redistributionRepository, eventEmitter, nhumeIntegration);
        tenantId = UUID.randomUUID();
    }

    @Test
    void requestEmergencyRedistributionPersistsRequest() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        when(redistributionRepository.save(any(BloodEmergencyRedistributionEntity.class)))
                .thenAnswer(invocation -> {
                    BloodEmergencyRedistributionEntity entity = invocation.getArgument(0);
                    entity.setRedistributionId(UUID.randomUUID());
                    return entity;
                });

        Map<String, Object> result = service.requestEmergencyRedistribution(tenantId, Map.of(
                "source_facility_id", source.toString(),
                "target_facility_id", target.toString(),
                "blood_group", "O",
                "component_type", "RED_CELLS",
                "units_requested", 4,
                "reason", "Mass casualty surge",
                "requested_by", "central-ops-1"
        ));

        assertThat(result.get("status")).isEqualTo("REQUESTED");
        assertThat(result.get("unitsRequested")).isEqualTo(4);
        ArgumentCaptor<BloodEmergencyRedistributionEntity> captor =
                ArgumentCaptor.forClass(BloodEmergencyRedistributionEntity.class);
        verify(redistributionRepository).save(captor.capture());
        assertThat(captor.getValue().getBloodGroup()).isEqualTo("O");
    }

    @Test
    void approveEmergencyRedistributionUpdatesStatus() {
        UUID redistributionId = UUID.randomUUID();
        BloodEmergencyRedistributionEntity existing = new BloodEmergencyRedistributionEntity();
        existing.setRedistributionId(redistributionId);
        existing.setTenantId(tenantId);
        existing.setSourceFacilityId(UUID.randomUUID());
        existing.setTargetFacilityId(UUID.randomUUID());
        existing.setBloodGroup("O");
        existing.setComponentType("RED_CELLS");
        existing.setStatus("REQUESTED");
        existing.setUnitsRequested(3);
        when(redistributionRepository.findByRedistributionIdAndTenantId(redistributionId, tenantId))
                .thenReturn(Optional.of(existing));
        when(redistributionRepository.save(any(BloodEmergencyRedistributionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UUID nhumeDeliveryId = UUID.randomUUID();
        when(nhumeIntegration.createBloodRedistributionDelivery(
                any(), any(), any(), anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(Optional.of(nhumeDeliveryId));

        Map<String, Object> result = service.approveEmergencyRedistribution(tenantId, redistributionId, Map.of(
                "approved_by", "supervisor-1",
                "units_allocated", 3,
                "status", "FULFILLED"
        ));

        assertThat(result.get("status")).isEqualTo("FULFILLED");
        assertThat(result.get("unitsAllocated")).isEqualTo(3);
        assertThat(result.get("approvedBy")).isEqualTo("supervisor-1");
        assertThat(result.get("nhumeDeliveryId")).isEqualTo(nhumeDeliveryId);
        assertThat(result.get("handoffStatus")).isEqualTo("DISPATCH_REQUESTED");
        assertThat(result.get("nhumeDeepLinkPath")).isEqualTo("/nhume/deliveries/" + nhumeDeliveryId);
    }
}
