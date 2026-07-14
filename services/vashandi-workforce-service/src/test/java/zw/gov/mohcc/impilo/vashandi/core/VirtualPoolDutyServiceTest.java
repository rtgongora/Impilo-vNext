package zw.gov.mohcc.impilo.vashandi.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ShiftEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VirtualPoolDutyServiceTest {

    @Mock private ShiftRepository shiftRepository;

    private final UUID tenant = UUID.randomUUID();

    private VirtualPoolDutyService service() {
        return new VirtualPoolDutyService(shiftRepository);
    }

    private ShiftEntity shift(String status, OffsetDateTime start, OffsetDateTime end) {
        ShiftEntity s = new ShiftEntity();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenant);
        s.setRosterId(UUID.randomUUID());
        s.setWorkforceProfileId(UUID.randomUUID());
        s.setShiftType("virtual_teleconsult");
        s.setStatus(status);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setVirtualPoolId("PROVINCIAL_ZW_HA");
        return s;
    }

    @Test
    void dutyVocabularyIsNarrowAndExcludesNonCoverageStatuses() {
        // The on-duty query MUST only count live coverage; a plan (scheduled) or a
        // dead shift (cancelled/swapped/missed/completed) is never coverage.
        assertThat(VirtualPoolDutyService.ON_DUTY_STATUSES)
                .containsExactly("confirmed", "checked_in")
                .doesNotContain("scheduled", "cancelled", "swapped", "missed", "completed", "late");
        assertThat(VirtualPoolDutyService.ROSTERED_STATUSES)
                .containsExactly("scheduled", "confirmed")
                .doesNotContain("cancelled", "swapped");
    }

    @Test
    void onDutySnapshotMapsShiftsCountAndNextStart() {
        OffsetDateTime now = OffsetDateTime.now();
        ShiftEntity onDuty = shift("checked_in", now.minusHours(1), now.plusHours(3));
        OffsetDateTime nextStart = now.plusHours(5);
        when(shiftRepository.findOnDutyForPool(eq(tenant), eq("PROVINCIAL_ZW_HA"),
                eq(VirtualPoolDutyService.ON_DUTY_STATUSES), any())).thenReturn(List.of(onDuty));
        when(shiftRepository.findNextShiftStartForPool(eq(tenant), eq("PROVINCIAL_ZW_HA"),
                eq(VirtualPoolDutyService.ROSTERED_STATUSES), any())).thenReturn(nextStart);

        Map<String, Object> snapshot = service().onDuty(tenant, "PROVINCIAL_ZW_HA", now);

        assertThat(snapshot.get("onDutyCount")).isEqualTo(1);
        assertThat(snapshot.get("nextShiftStart")).isEqualTo(nextStart.toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) snapshot.get("onDuty");
        assertThat(rows.get(0).get("workforceProfileId")).isEqualTo(onDuty.getWorkforceProfileId().toString());
        assertThat(rows.get(0).get("status")).isEqualTo("checked_in");
    }

    @Test
    void emptyPoolYieldsZeroCountAndNullNextStart() {
        OffsetDateTime now = OffsetDateTime.now();
        when(shiftRepository.findOnDutyForPool(eq(tenant), eq("EMPTY_POOL"), any(), any()))
                .thenReturn(List.of());
        when(shiftRepository.findNextShiftStartForPool(eq(tenant), eq("EMPTY_POOL"), any(), any()))
                .thenReturn(null);

        Map<String, Object> snapshot = service().onDuty(tenant, "EMPTY_POOL", now);

        assertThat(snapshot.get("onDutyCount")).isEqualTo(0);
        assertThat(snapshot.get("nextShiftStart")).isNull();
    }

    @Test
    void batchCoverageReturnsOneRowPerPool() {
        OffsetDateTime now = OffsetDateTime.now();
        when(shiftRepository.findOnDutyForPool(eq(tenant), eq("POOL_A"), any(), any()))
                .thenReturn(List.of(shift("confirmed", now.minusHours(1), now.plusHours(1))));
        when(shiftRepository.findOnDutyForPool(eq(tenant), eq("POOL_B"), any(), any()))
                .thenReturn(List.of());
        when(shiftRepository.findNextShiftStartForPool(eq(tenant), any(), any(), any())).thenReturn(null);

        List<Map<String, Object>> coverage = service().coverage(tenant, List.of("POOL_A", "POOL_B"), now);

        assertThat(coverage).hasSize(2);
        assertThat(coverage.get(0)).containsEntry("poolId", "POOL_A").containsEntry("onDutyCount", 1);
        assertThat(coverage.get(1)).containsEntry("poolId", "POOL_B").containsEntry("onDutyCount", 0);
    }
}
