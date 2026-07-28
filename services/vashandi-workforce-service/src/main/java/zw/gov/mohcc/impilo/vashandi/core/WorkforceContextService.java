package zw.gov.mohcc.impilo.vashandi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AttendanceEventEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.LeaveAvailabilityEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceMembershipEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.AttendanceEventRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.LeaveAvailabilityRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceMembershipRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Produces the C2 Workforce work-context read-model
 * (see docs/design/provider-clinical-place/shared-read-models.md § C2).
 *
 * Vashandi is the single producer. The shell context picker (T1) and the PCT
 * Cadre Engine consume this to drive the WHERE/WHAT chooser and clinical scope.
 *
 * This is a pure read assembly over existing Vashandi entities — no new SoR.
 */
@Service
public class WorkforceContextService {

    private static final Logger log = LoggerFactory.getLogger(WorkforceContextService.class);

    // Assignments considered "active context candidates" for the picker.
    private static final List<String> ACTIVE_STATUSES = List.of("active", "approved");

    private final WorkforceProfileRepository profileRepository;
    private final WorkforceAssignmentRepository assignmentRepository;
    private final WorkforceMembershipRepository membershipRepository;
    private final AttendanceEventRepository attendanceRepository;
    private final ShiftRepository shiftRepository;
    private final LeaveAvailabilityRepository leaveAvailabilityRepository;

    public WorkforceContextService(WorkforceProfileRepository profileRepository,
                                   WorkforceAssignmentRepository assignmentRepository,
                                   WorkforceMembershipRepository membershipRepository,
                                   AttendanceEventRepository attendanceRepository,
                                   ShiftRepository shiftRepository,
                                   LeaveAvailabilityRepository leaveAvailabilityRepository) {
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
        this.membershipRepository = membershipRepository;
        this.attendanceRepository = attendanceRepository;
        this.shiftRepository = shiftRepository;
        this.leaveAvailabilityRepository = leaveAvailabilityRepository;
    }

    /**
     * Resolve the active work-context for an actor by their Impilo Health ID.
     * Returns an unresolved (empty) context — never null, never an error — when
     * the actor has no workforce profile, so callers can safely render
     * "no work context" without leaking existence.
     */
    @Transactional(readOnly = true)
    public VashandiDtos.WorkforceContextResponse resolveByHealthId(UUID tenantId, String healthId) {
        if (healthId == null || healthId.isBlank()) {
            return VashandiDtos.WorkforceContextResponse.unresolved();
        }
        Optional<WorkforceProfileEntity> profileOpt =
                profileRepository.findByTenantIdAndHealthId(tenantId, healthId);
        if (profileOpt.isEmpty()) {
            log.debug("work-context: no workforce profile for actor in tenant={}", tenantId);
            return VashandiDtos.WorkforceContextResponse.unresolved();
        }
        return assemble(tenantId, profileOpt.get());
    }

    /** Resolve directly by workforce profile id (used by check-in flows that already hold it). */
    @Transactional(readOnly = true)
    public VashandiDtos.WorkforceContextResponse resolveByProfileId(UUID tenantId, UUID workforceProfileId) {
        if (workforceProfileId == null) {
            return VashandiDtos.WorkforceContextResponse.unresolved();
        }
        return profileRepository.findByTenantIdAndId(tenantId, workforceProfileId)
                .map(p -> assemble(tenantId, p))
                .orElseGet(VashandiDtos.WorkforceContextResponse::unresolved);
    }

    private VashandiDtos.WorkforceContextResponse assemble(UUID tenantId, WorkforceProfileEntity profile) {
        UUID profileId = profile.getId();

        List<VashandiDtos.WorkContextAssignment> assignments =
                assignmentRepository.findByTenantIdAndWorkforceProfileIdAndStatusIn(
                                tenantId, profileId, ACTIVE_STATUSES)
                        .stream()
                        .map(this::toAssignment)
                        .toList();

        List<VashandiDtos.WorkContextAffiliation> affiliations =
                membershipRepository.findByTenantIdAndWorkforceProfileIdAndStatusOrderByEffectiveDateDesc(
                                tenantId, profileId, "active")
                        .stream()
                        .map(this::toAffiliation)
                        .toList();

        VashandiDtos.WorkContextCheckIn checkIn = resolveCheckIn(tenantId, profileId);

        boolean requiresChooser = assignments.size() > 1;

        return new VashandiDtos.WorkforceContextResponse(
                profileId,
                profile.getHealthId(),
                assignments,
                checkIn,
                affiliations,
                requiresChooser,
                true);
    }

    private VashandiDtos.WorkContextCheckIn resolveCheckIn(UUID tenantId, UUID profileId) {
        // Latest attendance event (already ordered desc) determines current state.
        List<AttendanceEventEntity> events =
                attendanceRepository.findByTenantIdAndWorkforceProfileIdOrderByEventTimeDesc(tenantId, profileId);
        if (events.isEmpty()) {
            return new VashandiDtos.WorkContextCheckIn(null, "CHECKED_OUT", null, null, false);
        }
        AttendanceEventEntity latest = events.get(0);
        String type = latest.getEventType() == null ? "" : latest.getEventType();
        boolean checkedIn = type.equals("check_in") || type.equals("supervisor_confirmed_check_in");
        boolean supervisorConfirmed = type.startsWith("supervisor_confirmed");
        return new VashandiDtos.WorkContextCheckIn(
                latest.getShiftId() != null ? latest.getShiftId().toString() : null,
                checkedIn ? "CHECKED_IN" : "CHECKED_OUT",
                latest.getEventTime(),
                latest.getFacilityId(),
                supervisorConfirmed);
    }

    /**
     * Session-context read-model consumed by the experience BFF
     * ({@code VashandiSessionContextResolver}) when building the session
     * experience contract. Anti-enumeration: an unknown actor yields the same
     * shape with a {@code no_workforce_profile} resolution state, never a 404.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> sessionContext(UUID tenantId, String healthId, String providerWorkerId) {
        Optional<WorkforceProfileEntity> profileOpt = Optional.empty();
        if (healthId != null && !healthId.isBlank()) {
            profileOpt = profileRepository.findByTenantIdAndHealthId(tenantId, healthId);
        }
        if (profileOpt.isEmpty() && providerWorkerId != null && !providerWorkerId.isBlank()) {
            profileOpt = profileRepository.findByTenantIdAndProviderWorkerId(tenantId, providerWorkerId);
        }

        Map<String, Object> ctx = new LinkedHashMap<>();
        if (profileOpt.isEmpty()) {
            ctx.put("vashandiWorkforceProfileId", null);
            ctx.put("currentWorkforceStatus", null);
            ctx.put("currentAssignments", List.of());
            ctx.put("activeRosteredShifts", List.of());
            ctx.put("attendanceStatus", null);
            ctx.put("leaveAvailabilityStatus", null);
            ctx.put("workforceAccessRisks", List.of());
            ctx.put("vashandiFriendlyResolutionState", "no_workforce_profile");
            return ctx;
        }

        WorkforceProfileEntity profile = profileOpt.get();
        UUID profileId = profile.getId();

        List<VashandiDtos.WorkContextAssignment> assignments =
                assignmentRepository.findByTenantIdAndWorkforceProfileIdAndStatusIn(
                                tenantId, profileId, ACTIVE_STATUSES)
                        .stream()
                        .map(this::toAssignment)
                        .toList();

        VashandiDtos.WorkContextCheckIn checkIn = resolveCheckIn(tenantId, profileId);

        ctx.put("vashandiWorkforceProfileId", profileId.toString());
        ctx.put("currentWorkforceStatus", profile.getCurrentStatus());
        ctx.put("currentAssignments", assignments);
        ctx.put("activeRosteredShifts", activeRosteredShifts(tenantId, profileId));
        ctx.put("attendanceStatus", checkIn.state());
        ctx.put("leaveAvailabilityStatus", currentLeaveStatus(tenantId, profileId));
        ctx.put("workforceAccessRisks", List.of());
        ctx.put("vashandiFriendlyResolutionState", assignments.isEmpty() ? "no_active_assignment" : null);
        return ctx;
    }

    private List<Map<String, Object>> activeRosteredShifts(UUID tenantId, UUID profileId) {
        OffsetDateTime now = OffsetDateTime.now();
        return shiftRepository.findByTenantIdAndWorkforceProfileIdAndStartTimeBetweenOrderByStartTimeAsc(
                        tenantId, profileId, now.minusHours(12), now.plusHours(24))
                .stream()
                .map(s -> {
                    Map<String, Object> shift = new LinkedHashMap<String, Object>();
                    shift.put("shiftId", s.getId() != null ? s.getId().toString() : null);
                    shift.put("startTime", s.getStartTime() != null ? s.getStartTime().toString() : null);
                    shift.put("endTime", s.getEndTime() != null ? s.getEndTime().toString() : null);
                    shift.put("rosterId", s.getRosterId() != null ? s.getRosterId().toString() : null);
                    return shift;
                })
                .toList();
    }

    private String currentLeaveStatus(UUID tenantId, UUID profileId) {
        LocalDate today = LocalDate.now();
        List<LeaveAvailabilityEntity> current = leaveAvailabilityRepository
                .findByTenantIdAndWorkforceProfileIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        tenantId, profileId, "approved", today, today);
        return current.isEmpty() ? "available" : "on_leave";
    }

    private VashandiDtos.WorkContextAssignment toAssignment(WorkforceAssignmentEntity a) {
        return new VashandiDtos.WorkContextAssignment(
                a.getId(),
                upper(a.getStatus()),
                a.getOrganisationId(),
                a.getFacilityId(),
                a.getDepartmentId(),
                a.getUnitId(),
                a.getWorkspaceId(),
                a.getProgrammeId(),
                a.getRoleTemplateId(),
                a.getSupervisorProfileId(),
                deriveScope(a),
                a.getEligibilityStatus(),
                a.getVirtualPoolId(),
                a.getHostOrganisationId(),
                a.getCoverScopeJson(),
                a.getSupportTeamId(),
                a.getSupportAssignmentId());
    }

    private VashandiDtos.WorkContextAffiliation toAffiliation(WorkforceMembershipEntity m) {
        return new VashandiDtos.WorkContextAffiliation(
                m.getOrganisationId(),
                m.getMembershipType(),
                "primary".equalsIgnoreCase(m.getMembershipType()),
                m.getEffectiveDate(),
                m.getExpiryDate());
    }

    /**
     * Derive the narrowest WHERE scope an assignment grants, from its populated
     * context dimensions. Drives the WHERE/WHAT context picker granularity.
     */
    private String deriveScope(WorkforceAssignmentEntity a) {
        if (a.getUnitId() != null) {
            return "WARD";
        }
        if (a.getDepartmentId() != null) {
            return "DEPARTMENT";
        }
        if (a.getWorkspaceId() != null) {
            return "SERVICE_POINT";
        }
        if (a.getFacilityId() != null) {
            return "FACILITY";
        }
        // A4 fix: this previously mapped ANY programme-only assignment to
        // VIRTUAL_POOL, conflating two distinct context families (programme
        // management vs. virtual/telemedicine clinical work). virtualPoolId
        // is now the real, unambiguous signal for virtual/cross-facility
        // scope — a programme-only assignment (no facility, no virtual pool)
        // is a PROGRAMME-scoped context, not a virtual one.
        if (a.getVirtualPoolId() != null) {
            return "VIRTUAL_POOL";
        }
        if (a.getProgrammeId() != null) {
            return "PROGRAMME";
        }
        return "ABOVE_SITE";
    }

    private String upper(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }
}
