package zw.gov.mohcc.impilo.vashandi.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.integration.TusoIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.RosterEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ShiftEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.RosterRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Shift-create virtual-pool SOFT validation: accept-and-flag
 * (audit_metadata_json.unknownPool=true), never block, never hard-depend on
 * TUSO availability, and default OFF by configuration.
 */
@ExtendWith(MockitoExtension.class)
class RosterServiceVirtualPoolValidationTest {

    @Mock private RosterRepository rosterRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private VashandiOutboxWriter outboxWriter;
    @Mock private TusoIntegrationClient tusoIntegrationClient;

    private final UUID tenant = UUID.randomUUID();
    private final UUID rosterId = UUID.randomUUID();

    private RosterService service(boolean validationEnabled) {
        lenient().when(rosterRepository.findByTenantIdAndId(tenant, rosterId))
                .thenReturn(Optional.of(new RosterEntity()));
        lenient().when(shiftRepository.save(any())).thenAnswer(i -> {
            ShiftEntity s = i.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID()); // @PrePersist does this in JPA
            return s;
        });
        return new RosterService(rosterRepository, shiftRepository, outboxWriter,
                tusoIntegrationClient, validationEnabled);
    }

    private VashandiDtos.CreateShiftRequest shiftRequest(String poolId) {
        return new VashandiDtos.CreateShiftRequest(
                rosterId, UUID.randomUUID(), null, "virtual_teleconsult",
                OffsetDateTime.now(), OffsetDateTime.now().plusHours(8),
                "virtual", null, poolId, false);
    }

    @Test
    void unknownPoolIsAcceptedAndFlagged() throws Exception {
        when(tusoIntegrationClient.virtualPoolKnown("TYPO_POOL")).thenReturn(Optional.of(false));

        ShiftEntity saved = service(true).createShift(tenant, shiftRequest("TYPO_POOL"));

        assertThat(saved.getVirtualPoolId()).isEqualTo("TYPO_POOL"); // accepted
        assertThat(saved.getAuditMetadataJson()).contains("\"unknownPool\":true"); // flagged
    }

    @Test
    void knownPoolIsNotFlagged() throws Exception {
        when(tusoIntegrationClient.virtualPoolKnown("PROVINCIAL_ZW_HA")).thenReturn(Optional.of(true));

        ShiftEntity saved = service(true).createShift(tenant, shiftRequest("PROVINCIAL_ZW_HA"));

        assertThat(saved.getAuditMetadataJson()).isNull();
    }

    @Test
    void tusoUnreachableNeverBlocksAndNeverFlags() throws Exception {
        when(tusoIntegrationClient.virtualPoolKnown("PROVINCIAL_ZW_HA")).thenReturn(Optional.empty());

        ShiftEntity saved = service(true).createShift(tenant, shiftRequest("PROVINCIAL_ZW_HA"));

        assertThat(saved).isNotNull(); // creation succeeded despite no verification
        assertThat(saved.getAuditMetadataJson()).isNull(); // "cannot verify" is not "unknown"
    }

    @Test
    void validationDisabledByDefaultNeverCallsTuso() throws Exception {
        ShiftEntity saved = service(false).createShift(tenant, shiftRequest("ANY_POOL"));

        assertThat(saved).isNotNull();
        verify(tusoIntegrationClient, never()).virtualPoolKnown(any());
    }

    @Test
    void facilityShiftsSkipPoolValidationEntirely() throws Exception {
        ShiftEntity saved = service(true).createShift(tenant, shiftRequest(null));

        assertThat(saved).isNotNull();
        verify(tusoIntegrationClient, never()).virtualPoolKnown(any());
    }
}
