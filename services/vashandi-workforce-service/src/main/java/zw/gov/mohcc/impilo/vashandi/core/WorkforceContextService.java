package zw.gov.mohcc.impilo.vashandi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AttendanceEventEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceMembershipEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.AttendanceEventRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceMembershipRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.util.List;
import java.util.Locale;
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

    public WorkforceContextService(WorkforceProfileRepository profileRepository,
                                   WorkforceAssignmentRepository assignmentRepository,
                                   WorkforceMembershipRepository membershipRepository,
                                   AttendanceEventRepository attendanceRepository) {
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
        this.membershipRepository = membershipRepository;
        this.attendanceRepository = attendanceRepository;
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
                a.getEligibilityStatus());
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
        if (a.getProgrammeId() != null) {
            return "VIRTUAL_POOL";
        }
        return "ABOVE_SITE";
    }

    private String upper(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }
}
