package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.BedEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.BedRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.WardRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ward provisioning (route-contract completion).
 *
 * <p>The BFF's ward-create call went to {@code tuso /v1/wards}, which nothing serves, while ward
 * reads already resolved to {@code inpatient.ward}. These cases pin the write path now that it
 * lands in the same place as the reads — and pin the safety-default decision, which is the part
 * that can hurt someone.</p>
 */
@ExtendWith(MockitoExtension.class)
class WardProvisioningTest {

    @Mock private WardRepository wardRepository;
    @Mock private BedRepository bedRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private BedManagementService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BedManagementService(wardRepository, bedRepository,
                new BedSafetyEvaluator(), outboxRepository, new ObjectMapper());
    }

    private BedManagementService.CreateWardCommand command(int beds, Boolean oxygen) {
        return new BedManagementService.CreateWardCommand(
                facilityId, "Maternity A", "MATERNITY", "Ground", beds,
                "FEMALE", "ADULT", null, oxygen, null, null);
    }

    @SuppressWarnings("unchecked")
    private List<BedEntity> capturedBeds() {
        ArgumentCaptor<List<BedEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(bedRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    // ── the write lands, with its beds ──────────────────────────────────────

    @Test
    void provisioningAWardMaterialisesItsBeds() {
        when(wardRepository.findByFacilityIdOrderByNameAsc(facilityId)).thenReturn(List.of());

        Map<String, Object> result = service.createWard(tenantId, command(4, true));

        assertThat(capturedBeds()).hasSize(4);
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) result.get("attributes");
        assertThat(attributes).containsEntry("totalBeds", 4).containsEntry("availableBeds", 4);
    }

    @Test
    void bedsAreUniquelyNumberedAndStartAvailable() {
        when(wardRepository.findByFacilityIdOrderByNameAsc(facilityId)).thenReturn(List.of());

        service.createWard(tenantId, command(3, true));

        List<BedEntity> beds = capturedBeds();
        assertThat(beds).extracting(BedEntity::getBedNumber).doesNotHaveDuplicates();
        assertThat(beds).allSatisfy(b -> {
            assertThat(b.getStatus()).isEqualTo("AVAILABLE");
            assertThat(b.getWardId()).isNotNull();
            assertThat(b.getTenantId()).isEqualTo(tenantId);
        });
    }

    // ── the part that can hurt someone ──────────────────────────────────────

    /**
     * The V015 column default for oxygen_available is TRUE. If provisioning inherited that, a ward
     * created without stating its capabilities would claim oxygen it may not have, and
     * BedSafetyEvaluator would then admit an oxygen-dependent patient to a ward with none. An
     * undeclared capability is absent.
     */
    @Test
    void anUndeclaredCapabilityIsAbsentNotAssumed() {
        when(wardRepository.findByFacilityIdOrderByNameAsc(facilityId)).thenReturn(List.of());

        service.createWard(tenantId, new BedManagementService.CreateWardCommand(
                facilityId, "Ward B", "GENERAL", null, 1, null, null, null, null, null, null));

        ArgumentCaptor<WardEntity> ward = ArgumentCaptor.forClass(WardEntity.class);
        verify(wardRepository).save(ward.capture());
        assertThat(ward.getValue().isOxygenAvailable())
                .as("oxygen must not be assumed from the column default")
                .isFalse();
        assertThat(ward.getValue().isIsolationCapable()).isFalse();
        assertThat(ward.getValue().isMonitoringCapable()).isFalse();
        assertThat(ward.getValue().isIcuCapable()).isFalse();
    }

    @Test
    void declaredCapabilitiesAreHonoured() {
        when(wardRepository.findByFacilityIdOrderByNameAsc(facilityId)).thenReturn(List.of());

        service.createWard(tenantId, new BedManagementService.CreateWardCommand(
                facilityId, "ICU", "ICU", null, 2, "ANY", "ADULT", true, true, true, true));

        ArgumentCaptor<WardEntity> ward = ArgumentCaptor.forClass(WardEntity.class);
        verify(wardRepository).save(ward.capture());
        assertThat(ward.getValue().isIcuCapable()).isTrue();
        assertThat(ward.getValue().isOxygenAvailable()).isTrue();
    }

    /** A ward's capabilities are not copied onto its beds — one claim, not two that can drift. */
    @Test
    void bedsDoNotDuplicateTheWardsCapabilityClaim() {
        when(wardRepository.findByFacilityIdOrderByNameAsc(facilityId)).thenReturn(List.of());

        service.createWard(tenantId, new BedManagementService.CreateWardCommand(
                facilityId, "ICU", "ICU", null, 2, "ANY", "ADULT", true, true, true, true));

        assertThat(capturedBeds()).allSatisfy(b -> assertThat(b.isIcuCapable()).isFalse());
    }

    // ── refusals ────────────────────────────────────────────────────────────

    @Test
    void aDuplicateWardNameAtTheSameFacilityIsRefused() {
        WardEntity existing = new WardEntity();
        existing.setId(UUID.randomUUID());
        existing.setName("Maternity A");
        existing.setFacilityId(facilityId);
        when(wardRepository.findByFacilityIdOrderByNameAsc(facilityId)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createWard(tenantId, command(2, true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void aWardWithNoBedsIsRefused() {
        assertThatThrownBy(() -> service.createWard(tenantId, command(0, true)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(bedRepository, org.mockito.Mockito.never()).saveAll(any());
    }

    // ── the event ───────────────────────────────────────────────────────────

    @Test
    void provisioningEmitsAWardEventCarryingTheCapabilityClaim() {
        when(wardRepository.findByFacilityIdOrderByNameAsc(facilityId)).thenReturn(List.of());

        service.createWard(tenantId, command(2, false));

        ArgumentCaptor<EventOutboxEntity> event = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(event.capture());
        assertThat(event.getValue().getAggregateType()).isEqualTo("WARD");
        assertThat(event.getValue().getEventType()).isEqualTo("inpatient.ward.provisioned");
        assertThat(event.getValue().getPayloadJson())
                .contains("\"total_beds\":2")
                .contains("\"oxygen_available\":false");
    }
}
