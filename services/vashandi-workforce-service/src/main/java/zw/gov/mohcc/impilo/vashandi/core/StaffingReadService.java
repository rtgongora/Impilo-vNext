package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ShiftEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ShiftSwapRequestEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftSwapRequestRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The staffing read-models and the shift-swap workflow (V009).
 *
 * <p><b>What this completes.</b> experience-bff has been calling tuso for
 * {@code /v1/staffing/roster-week}, {@code /v1/staffing/on-call} and {@code /v1/staffing/on-call/swaps}.
 * Nothing in the estate serves those paths, so the roster screen, the on-call screen and the
 * control tower have always rendered empty while every layer reported success. Vashandi owns
 * rostering, so the surface lands here.</p>
 *
 * <p><b>One on-call truth, several read shapes.</b> Scheduled on-call is not a second store: it is
 * a projection over {@code vsh_shift} rows that carry an {@code on_call_role}. That keeps it
 * consistent with {@link VirtualPoolDutyService}, which reads the same rows for live duty
 * ("who is on now") through the frozen TUSO pool-key seam. A shift is rostered once and read
 * several ways.</p>
 *
 * <p><b>What this service will not invent.</b> {@code vsh_workforce_profile} holds no name and no
 * telephone number — person identity belongs to vito and provider identity to varapi. So rows carry
 * profile references and leave display names to the composition layer, and the on-call contact
 * number is returned as null when it is not known. A plausible-looking phone number on an on-call
 * rota is the sort of fallback somebody dials at three in the morning.</p>
 */
@Service
public class StaffingReadService {

    public static final String ROLE_PRIMARY = "PRIMARY";
    public static final String ROLE_BACKUP = "BACKUP";

    private static final String STATUS_PENDING = "PENDING";

    private final ShiftRepository shiftRepository;
    private final ShiftSwapRequestRepository swapRepository;
    private final WorkforceProfileRepository profileRepository;
    private final VashandiOutboxWriter outboxWriter;

    public StaffingReadService(ShiftRepository shiftRepository,
                               ShiftSwapRequestRepository swapRepository,
                               WorkforceProfileRepository profileRepository,
                               VashandiOutboxWriter outboxWriter) {
        this.shiftRepository = shiftRepository;
        this.swapRepository = swapRepository;
        this.profileRepository = profileRepository;
        this.outboxWriter = outboxWriter;
    }

    /**
     * The most human-readable reference vashandi actually holds for a person: their operational
     * worker id. It is NOT their name — names belong to vito and varapi — so it is returned under
     * a field that says "reference", and a caller that wants a name must resolve one upstream.
     * Returning the worker id beats returning a bare UUID, and beats inventing a name outright.
     */
    private String staffReference(UUID tenantId, UUID profileId) {
        return profileRepository.findByTenantIdAndId(tenantId, profileId)
                .map(p -> p.getProviderWorkerId() != null && !p.getProviderWorkerId().isBlank()
                        ? p.getProviderWorkerId()
                        : p.getHealthId())
                .orElse(null);
    }

    // ── roster-week ─────────────────────────────────────────────────────────

    /** Every shift at a facility in the seven days from {@code weekStart}. */
    public List<Map<String, Object>> rosterWeek(UUID tenantId, UUID facilityId, LocalDate weekStart) {
        OffsetDateTime from = weekStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        List<ShiftEntity> shifts = shiftRepository
                .findByTenantIdAndFacilityIdAndStartTimeBetweenOrderByStartTimeAsc(
                        tenantId, facilityId, from, from.plusDays(7));
        return shifts.stream().map(s -> shiftRow(s, staffReference(tenantId, s.getWorkforceProfileId()))).toList();
    }

    private static Map<String, Object> shiftRow(ShiftEntity s, String staffReference) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        // The person reference vashandi actually holds. The display name is resolved upstream by
        // the composition layer; vashandi does not keep a second copy of anyone's name.
        attributes.put("workforce_profile_id", s.getWorkforceProfileId().toString());
        attributes.put("staff_reference", staffReference);
        attributes.put("facility_id", s.getFacilityId() == null ? null : s.getFacilityId().toString());
        attributes.put("shift_type", s.getShiftType());
        attributes.put("on_call_role", s.getOnCallRole());
        attributes.put("specialty", s.getSpecialty());
        attributes.put("status", s.getStatus());
        attributes.put("started_at", s.getStartTime().toString());
        attributes.put("ended_at", s.getEndTime().toString());
        attributes.put("virtual_pool_id", s.getVirtualPoolId());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", s.getId().toString());
        row.put("type", "shift");
        row.put("attributes", attributes);
        return row;
    }

    // ── on-call rota ────────────────────────────────────────────────────────

    /**
     * The week's on-call rota, one row per (date, specialty), pairing first call with second call.
     *
     * <p>A day with a primary and no backup is reported with a null backup rather than omitted —
     * "nobody is second on call tonight" is the single most useful thing this screen can say, and
     * dropping the row would render it as though the day were covered.</p>
     */
    public List<Map<String, Object>> onCallWeek(UUID tenantId, UUID facilityId, LocalDate weekStart) {
        OffsetDateTime from = weekStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        List<ShiftEntity> shifts = shiftRepository.findOnCallForFacilityWindow(
                tenantId, facilityId, from, from.plusDays(7));

        Map<String, Map<String, Object>> byDayAndSpecialty = new LinkedHashMap<>();
        for (ShiftEntity s : shifts) {
            String date = s.getStartTime().toLocalDate().toString();
            String specialty = s.getSpecialty() == null ? "GENERAL" : s.getSpecialty();
            String key = date + "|" + specialty;

            Map<String, Object> row = byDayAndSpecialty.computeIfAbsent(key, k -> {
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("assignment_date", date);
                attributes.put("specialty", specialty);
                attributes.put("shift_kind", s.getShiftType());
                attributes.put("primary_workforce_profile_id", null);
                attributes.put("primary_staff_reference", null);
                attributes.put("primary_shift_id", null);
                // vashandi holds no telephone numbers; null means "not known here", never "none".
                attributes.put("primary_phone", null);
                attributes.put("backup_workforce_profile_id", null);
                attributes.put("backup_staff_reference", null);
                attributes.put("backup_shift_id", null);
                attributes.put("backup_phone", null);

                Map<String, Object> out = new LinkedHashMap<>();
                out.put("id", key);
                out.put("type", "on-call-assignment");
                out.put("attributes", attributes);
                return out;
            });

            @SuppressWarnings("unchecked")
            Map<String, Object> attributes = (Map<String, Object>) row.get("attributes");
            if (ROLE_BACKUP.equals(s.getOnCallRole())) {
                attributes.put("backup_workforce_profile_id", s.getWorkforceProfileId().toString());
                attributes.put("backup_staff_reference", staffReference(tenantId, s.getWorkforceProfileId()));
                attributes.put("backup_shift_id", s.getId().toString());
            } else {
                attributes.put("primary_workforce_profile_id", s.getWorkforceProfileId().toString());
                attributes.put("primary_staff_reference", staffReference(tenantId, s.getWorkforceProfileId()));
                attributes.put("primary_shift_id", s.getId().toString());
            }
        }
        return List.copyOf(byDayAndSpecialty.values());
    }

    // ── swaps ───────────────────────────────────────────────────────────────

    public List<Map<String, Object>> listSwaps(UUID tenantId, UUID facilityId, String status) {
        List<ShiftSwapRequestEntity> rows = (status == null || status.isBlank())
                ? swapRepository.findByTenantIdAndFacilityIdOrderByCreatedAtDesc(tenantId, facilityId)
                : swapRepository.findByTenantIdAndFacilityIdAndStatusOrderByCreatedAtDesc(
                        tenantId, facilityId, status.trim().toUpperCase());
        return rows.stream().map(StaffingReadService::swapRow).toList();
    }

    /** Raise a swap request against a rostered shift. */
    @Transactional
    public Map<String, Object> createSwap(UUID tenantId, UUID requestingShiftId, UUID requestedProfileId,
                                          UUID offeredShiftId, String reason, String actor) throws Exception {
        ShiftEntity shift = shiftRepository.findById(requestingShiftId)
                .orElseThrow(() -> new IllegalArgumentException("shift not found: " + requestingShiftId));
        if (!tenantId.equals(shift.getTenantId())) {
            throw new IllegalArgumentException("shift not found: " + requestingShiftId);
        }
        if (shift.getWorkforceProfileId().equals(requestedProfileId)) {
            throw new IllegalArgumentException(
                    "the shift already belongs to that person — a swap with oneself changes nothing");
        }
        // Checked here as well as by the partial unique index, so the caller gets an explanation
        // rather than a raw constraint violation.
        if (swapRepository.findByRequestingShiftIdAndStatus(requestingShiftId, STATUS_PENDING).isPresent()) {
            throw new IllegalStateException(
                    "a swap request is already open for this shift; withdraw or decide it first");
        }

        ShiftSwapRequestEntity swap = new ShiftSwapRequestEntity();
        swap.setTenantId(tenantId);
        swap.setFacilityId(shift.getFacilityId());
        swap.setRequestingShiftId(requestingShiftId);
        swap.setRequestingProfileId(shift.getWorkforceProfileId());
        swap.setRequestedProfileId(requestedProfileId);
        swap.setOfferedShiftId(offeredShiftId);
        swap.setReason(reason);
        swap.setStatus(STATUS_PENDING);
        swap.setCreatedBy(actor);
        ShiftSwapRequestEntity saved = swapRepository.save(swap);

        outboxWriter.publish(tenantId, "SHIFT_SWAP", saved.getId().toString(), "shift_swap", "requested",
                "vashandi:shift-swap:requested:" + saved.getId(), swapPayload(saved));
        return swapRow(saved);
    }

    /**
     * Decide a swap. Approval moves the requesting shift to the other person and marks the original
     * {@code swapped} — without that the rota would still show the person who is no longer coming.
     */
    @Transactional
    public Map<String, Object> decideSwap(UUID tenantId, UUID swapId, String decision,
                                          String decidedBy, String note) throws Exception {
        ShiftSwapRequestEntity swap = swapRepository.findByTenantIdAndId(tenantId, swapId)
                .orElseThrow(() -> new IllegalArgumentException("swap request not found: " + swapId));
        String target = decision == null ? "" : decision.trim().toUpperCase();
        if (!List.of("APPROVED", "DECLINED", "WITHDRAWN").contains(target)) {
            throw new IllegalArgumentException("decision must be APPROVED, DECLINED or WITHDRAWN");
        }
        if (!STATUS_PENDING.equals(swap.getStatus())) {
            throw new IllegalStateException("swap request is already " + swap.getStatus());
        }
        if (!"WITHDRAWN".equals(target) && (decidedBy == null || decidedBy.isBlank())) {
            throw new IllegalArgumentException(
                    "a decision must record who made it — a swap changes who is clinically accountable");
        }

        swap.setStatus(target);
        swap.setDecidedBy(decidedBy);
        swap.setDecidedAt(OffsetDateTime.now());
        swap.setDecisionNote(note);
        ShiftSwapRequestEntity saved = swapRepository.save(swap);

        if ("APPROVED".equals(target)) {
            shiftRepository.findById(swap.getRequestingShiftId()).ifPresent(shift -> {
                shift.setWorkforceProfileId(swap.getRequestedProfileId());
                shift.setStatus("swapped");
                shiftRepository.save(shift);
            });
        }

        outboxWriter.publish(tenantId, "SHIFT_SWAP", saved.getId().toString(), "shift_swap",
                target.toLowerCase(), "vashandi:shift-swap:" + target.toLowerCase() + ":" + saved.getId(),
                swapPayload(saved));
        return swapRow(saved);
    }

    private static Map<String, Object> swapRow(ShiftSwapRequestEntity s) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("requesting_shift_id", s.getRequestingShiftId().toString());
        attributes.put("requesting_workforce_profile_id", s.getRequestingProfileId().toString());
        attributes.put("requested_workforce_profile_id", s.getRequestedProfileId().toString());
        attributes.put("offered_shift_id", s.getOfferedShiftId() == null ? null : s.getOfferedShiftId().toString());
        attributes.put("facility_id", s.getFacilityId() == null ? null : s.getFacilityId().toString());
        attributes.put("reason", s.getReason());
        attributes.put("status", s.getStatus());
        attributes.put("decided_by", s.getDecidedBy());
        attributes.put("decided_at", s.getDecidedAt() == null ? null : s.getDecidedAt().toString());
        attributes.put("created_at", s.getCreatedAt() == null ? null : s.getCreatedAt().toString());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", s.getId().toString());
        row.put("type", "shift-swap-request");
        row.put("attributes", attributes);
        return row;
    }

    private static Map<String, Object> swapPayload(ShiftSwapRequestEntity s) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("swapRequestId", s.getId().toString());
        payload.put("requestingShiftId", s.getRequestingShiftId().toString());
        payload.put("requestingProfileId", s.getRequestingProfileId().toString());
        payload.put("requestedProfileId", s.getRequestedProfileId().toString());
        payload.put("facilityId", s.getFacilityId() == null ? "" : s.getFacilityId().toString());
        payload.put("status", s.getStatus());
        return payload;
    }
}
