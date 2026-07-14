package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ShiftEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Virtual-pool duty read-model: who is on duty for a pool right now, and when
 * the next rostered coverage starts. The pool key is the TUSO routing seam
 * target_ref (frozen contract) carried on {@code vsh_shift.virtual_pool_id}.
 *
 * <p>On-duty truth is deliberately narrow: a shift counts only when its status
 * is {@code confirmed} or {@code checked_in} AND the shift window covers now.
 * {@code scheduled} is a plan (surfaced via nextShiftStart), and
 * cancelled/swapped/missed/completed shifts are never coverage.</p>
 */
@Service
public class VirtualPoolDutyService {

    /** Statuses that count as live coverage. */
    public static final List<String> ON_DUTY_STATUSES = List.of("confirmed", "checked_in");

    /** Statuses that count as future rostered coverage (lookahead only). */
    public static final List<String> ROSTERED_STATUSES = List.of("scheduled", "confirmed");

    private final ShiftRepository shiftRepository;

    public VirtualPoolDutyService(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    /** Duty snapshot for one pool: on-duty providers + windows + next rostered start. */
    public Map<String, Object> onDuty(UUID tenantId, String poolId, OffsetDateTime at) {
        List<ShiftEntity> shifts = shiftRepository.findOnDutyForPool(tenantId, poolId, ON_DUTY_STATUSES, at);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("poolId", poolId);
        out.put("asOf", at.toString());
        out.put("onDutyCount", shifts.size());
        out.put("onDuty", shifts.stream().map(s -> {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("shiftId", s.getId().toString());
            row.put("workforceProfileId", s.getWorkforceProfileId().toString());
            row.put("shiftType", s.getShiftType());
            row.put("status", s.getStatus());
            row.put("startTime", s.getStartTime().toString());
            row.put("endTime", s.getEndTime().toString());
            return row;
        }).toList());
        OffsetDateTime nextStart = shiftRepository.findNextShiftStartForPool(tenantId, poolId, ROSTERED_STATUSES, at);
        out.put("nextShiftStart", nextStart == null ? null : nextStart.toString());
        return out;
    }

    /** Batch coverage counts for several pools (BFF composition read). */
    public List<Map<String, Object>> coverage(UUID tenantId, List<String> poolIds, OffsetDateTime at) {
        return poolIds.stream().map(poolId -> {
            List<ShiftEntity> shifts = shiftRepository.findOnDutyForPool(tenantId, poolId, ON_DUTY_STATUSES, at);
            OffsetDateTime nextStart = shiftRepository.findNextShiftStartForPool(
                    tenantId, poolId, ROSTERED_STATUSES, at);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("poolId", poolId);
            row.put("onDutyCount", shifts.size());
            row.put("nextShiftStart", nextStart == null ? null : nextStart.toString());
            return row;
        }).toList();
    }
}
