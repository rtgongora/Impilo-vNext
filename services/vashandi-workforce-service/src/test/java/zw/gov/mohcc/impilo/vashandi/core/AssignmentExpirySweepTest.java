package zw.gov.mohcc.impilo.vashandi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V008/D-P7: a lapsed active assignment flips to ended and the emitted event
 * carries the person anchor + facility for trust-plane token teardown; nothing
 * lapsed means no writes.
 */
@ExtendWith(MockitoExtension.class)
class AssignmentExpirySweepTest {

    @Mock private WorkforceAssignmentRepository assignmentRepository;
    @Mock private WorkforceProfileRepository profileRepository;
    @Mock private VashandiOutboxWriter outboxWriter;

    private AssignmentExpirySweep sweep;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sweep = new AssignmentExpirySweep(assignmentRepository, profileRepository, outboxWriter);
        lenient().when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void lapsedLocumEndsAndEmitsEventWithPersonAnchor() throws Exception {
        WorkforceAssignmentEntity locum = new WorkforceAssignmentEntity();
        locum.setId(UUID.randomUUID());
        locum.setTenantId(tenantId);
        locum.setWorkforceProfileId(profileId);
        locum.setAssignmentType("clinical");
        locum.setEngagementType("LOCUM");
        locum.setFacilityId(facilityId);
        locum.setStatus(AssignmentExpirySweep.STATUS_ACTIVE);
        locum.setEndDate(LocalDate.of(2026, 6, 30));
        when(assignmentRepository.findByStatusAndEndDateBefore(
                AssignmentExpirySweep.STATUS_ACTIVE, LocalDate.of(2026, 7, 19)))
                .thenReturn(List.of(locum));

        WorkforceProfileEntity profile = mock(WorkforceProfileEntity.class);
        when(profile.getHealthId()).thenReturn("HID-77");
        when(profileRepository.findById(profileId)).thenReturn(Optional.of(profile));

        int ended = sweep.endLapsed(LocalDate.of(2026, 7, 19));

        assertThat(ended).isEqualTo(1);
        assertThat(locum.getStatus()).isEqualTo(AssignmentExpirySweep.STATUS_ENDED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxWriter).publish(eq(tenantId), eq("WORKFORCE_ASSIGNMENT"),
                eq(locum.getId().toString()), eq("assignment"), eq("ended"),
                eq("assignment-ended:" + locum.getId()), payload.capture());
        assertThat(payload.getValue().get("impiloHealthId")).isEqualTo("HID-77");
        assertThat(payload.getValue().get("facilityId")).isEqualTo(facilityId.toString());
        assertThat(payload.getValue().get("engagementType")).isEqualTo("LOCUM");
    }

    @Test
    void nothingLapsedMeansNoWrites() throws Exception {
        when(assignmentRepository.findByStatusAndEndDateBefore(any(), any())).thenReturn(List.of());

        assertThat(sweep.endLapsed(LocalDate.now())).isZero();
        verify(assignmentRepository, never()).save(any());
        verify(outboxWriter, never()).publish(any(), any(), any(), any(), any(), any(), any());
    }
}
