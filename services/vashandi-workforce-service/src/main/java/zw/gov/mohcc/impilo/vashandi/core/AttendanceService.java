package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AttendanceEventEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ShiftEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.AttendanceEventRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AttendanceService {

    private final AttendanceEventRepository attendanceRepository;
    private final ShiftRepository shiftRepository;
    private final WorkforceAssignmentRepository assignmentRepository;
    private final VashandiOutboxWriter outboxWriter;

    private final zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient biometricVerification;

    public AttendanceService(AttendanceEventRepository attendanceRepository,
                             ShiftRepository shiftRepository,
                             WorkforceAssignmentRepository assignmentRepository,
                             VashandiOutboxWriter outboxWriter,
                             zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient biometricVerification) {
        this.attendanceRepository = attendanceRepository;
        this.shiftRepository = shiftRepository;
        this.assignmentRepository = assignmentRepository;
        this.outboxWriter = outboxWriter;
        this.biometricVerification = biometricVerification;
    }

    /**
     * Resolve the check-in mode, verifying a biometric through the shared seam when
     * one is supplied. MATCH → "biometric"; NO_MATCH → null (caller denies); engine
     * UNAVAILABLE → fall back to the requested mode (a biometric outage must not
     * block a worker clocking in).
     */
    private String resolveCheckInMode(VashandiDtos.CheckInRequest request, String defaultMode) {
        String requested = request.checkInMode() != null ? request.checkInMode() : defaultMode;
        if (request.biometricProbeBase64() == null || request.biometricProbeBase64().isBlank()
                || request.biometricSubjectRef() == null || request.biometricSubjectRef().isBlank()) {
            return requested;
        }
        String modality = request.biometricModality() != null ? request.biometricModality() : "FINGERPRINT";
        var decision = biometricVerification.verify(
                request.biometricSubjectRef(), modality, request.biometricProbeBase64());
        if (decision.isMatch()) {
            return "biometric";
        }
        if ("UNAVAILABLE".equals(decision.result()) || "NO_REFERENCE".equals(decision.result())) {
            return requested; // care-first: don't block clock-in on a biometric outage
        }
        return null; // NO_MATCH → deny
    }

    public List<AttendanceEventEntity> list(UUID tenantId, UUID workforceProfileId) {
        if (workforceProfileId != null) {
            return attendanceRepository.findByTenantIdAndWorkforceProfileIdOrderByEventTimeDesc(tenantId, workforceProfileId);
        }
        return attendanceRepository.findByTenantIdOrderByEventTimeDesc(tenantId);
    }

    @Transactional
    public VashandiDtos.AttendanceActionResponse checkIn(UUID tenantId, VashandiDtos.CheckInRequest request)
            throws Exception {
        ShiftEntity shift = null;
        if (request.shiftId() != null) {
            shift = shiftRepository.findByTenantIdAndId(tenantId, request.shiftId()).orElse(null);
            if (shift == null) {
                return new VashandiDtos.AttendanceActionResponse(null, "denied", "shift not found");
            }
            if (!"scheduled".equals(shift.getStatus()) && !"confirmed".equals(shift.getStatus())) {
                return new VashandiDtos.AttendanceActionResponse(null, "denied", "shift not open for check-in");
            }
        }

        String mode = resolveCheckInMode(request, "self_check_in");
        if (mode == null) {
            return new VashandiDtos.AttendanceActionResponse(null, "denied", "biometric verification failed");
        }

        AttendanceEventEntity event = new AttendanceEventEntity();
        event.setTenantId(tenantId);
        event.setShiftId(request.shiftId());
        event.setWorkforceProfileId(request.workforceProfileId());
        event.setEventType("check_in");
        event.setEventTime(request.eventTime() != null ? request.eventTime() : OffsetDateTime.now());
        event.setCheckInMode(mode);
        event.setDeviceId(request.deviceId());
        event.setOffline(request.offline() != null && request.offline());
        AttendanceEventEntity saved = attendanceRepository.save(event);

        if (shift != null) {
            shift.setStatus("checked_in");
            shiftRepository.save(shift);
        }

        emit(tenantId, saved, "checked_in");
        return new VashandiDtos.AttendanceActionResponse(saved.getId(), "completed", "checked in");
    }

    /**
     * Ad-hoc check-in: a worker checks in against an active assignment (or a
     * facility/department/unit context) without a pre-rostered shift. This closes
     * the gap where the only check-in path required a {@code shiftId}.
     *
     * Enforces WORK-REQUIRES-ASSIGNMENT at the data layer: if an assignment id is
     * supplied it must belong to this profile and be in an active/approved state.
     * Policy authorisation (CHECKIN-SCOPE, WORK-REQUIRES-ASSIGNMENT) is specced to
     * track P and applied at the ext_authz gate; this is the SoR-side guard.
     */
    @Transactional
    public VashandiDtos.AttendanceActionResponse adhocCheckIn(UUID tenantId, VashandiDtos.AdhocCheckInRequest request)
            throws Exception {
        if (request.workforceProfileId() == null) {
            return new VashandiDtos.AttendanceActionResponse(null, "denied", "workforce profile required");
        }

        UUID facilityId = request.facilityId();
        String departmentId = request.departmentId();
        String unitId = request.unitId();
        UUID workspaceId = request.workspaceId();
        String contextScope = request.contextScope();

        if (request.assignmentId() != null) {
            WorkforceAssignmentEntity assignment =
                    assignmentRepository.findByTenantIdAndId(tenantId, request.assignmentId()).orElse(null);
            if (assignment == null
                    || !request.workforceProfileId().equals(assignment.getWorkforceProfileId())) {
                // Work-requires-assignment is enforced here in-service: deny when the assignment is
                // absent or does not belong to this worker. (Mirroring this binding deny into the
                // PDP/OPA is future defense-in-depth; baseline endpoint access is gated at ext_authz.)
                return new VashandiDtos.AttendanceActionResponse(null, "denied", "assignment not found for worker");
            }
            if (!"active".equals(assignment.getStatus()) && !"approved".equals(assignment.getStatus())) {
                return new VashandiDtos.AttendanceActionResponse(null, "denied", "assignment not active");
            }
            // Default the check-in context from the assignment when not overridden.
            if (facilityId == null) {
                facilityId = assignment.getFacilityId();
            }
            if (departmentId == null) {
                departmentId = assignment.getDepartmentId();
            }
            if (unitId == null) {
                unitId = assignment.getUnitId();
            }
            if (workspaceId == null) {
                workspaceId = assignment.getWorkspaceId();
            }
        } else if (facilityId == null) {
            // No assignment and no facility context — nothing to anchor the check-in to.
            return new VashandiDtos.AttendanceActionResponse(null, "denied", "assignment or facility context required");
        }

        AttendanceEventEntity event = new AttendanceEventEntity();
        event.setTenantId(tenantId);
        event.setWorkforceProfileId(request.workforceProfileId());
        event.setAssignmentId(request.assignmentId());
        event.setFacilityId(facilityId);
        event.setDepartmentId(departmentId);
        event.setUnitId(unitId);
        event.setWorkspaceId(workspaceId);
        event.setContextScope(contextScope);
        event.setAdhoc(true);
        event.setEventType("check_in");
        event.setEventTime(request.eventTime() != null ? request.eventTime() : OffsetDateTime.now());
        event.setCheckInMode(request.checkInMode() != null ? request.checkInMode() : "adhoc_self_check_in");
        event.setDeviceId(request.deviceId());
        event.setOffline(request.offline() != null && request.offline());
        AttendanceEventEntity saved = attendanceRepository.save(event);

        emit(tenantId, saved, "checked_in");
        return new VashandiDtos.AttendanceActionResponse(saved.getId(), "completed", "checked in (ad-hoc)");
    }

    @Transactional
    public VashandiDtos.AttendanceActionResponse checkOut(UUID tenantId, VashandiDtos.CheckOutRequest request)
            throws Exception {
        AttendanceEventEntity event = new AttendanceEventEntity();
        event.setTenantId(tenantId);
        event.setShiftId(request.shiftId());
        event.setWorkforceProfileId(request.workforceProfileId());
        event.setEventType("check_out");
        event.setEventTime(request.eventTime() != null ? request.eventTime() : OffsetDateTime.now());
        event.setCheckInMode(request.checkInMode());
        event.setOffline(request.offline() != null && request.offline());
        AttendanceEventEntity saved = attendanceRepository.save(event);

        if (request.shiftId() != null) {
            shiftRepository.findByTenantIdAndId(tenantId, request.shiftId()).ifPresent(shift -> {
                shift.setStatus("checked_out");
                shiftRepository.save(shift);
            });
        }

        emit(tenantId, saved, "checked_out");
        return new VashandiDtos.AttendanceActionResponse(saved.getId(), "completed", "checked out");
    }

    @Transactional
    public VashandiDtos.AttendanceActionResponse supervisorConfirm(UUID tenantId,
                                                                   VashandiDtos.SupervisorConfirmRequest request)
            throws Exception {
        AttendanceEventEntity event = new AttendanceEventEntity();
        event.setTenantId(tenantId);
        event.setShiftId(request.shiftId());
        event.setWorkforceProfileId(request.workforceProfileId());
        event.setEventType("supervisor_confirmed_check_in".equals(request.confirmationType())
                ? "supervisor_confirmed_check_in" : "supervisor_confirmed_check_out");
        event.setEventTime(OffsetDateTime.now());
        event.setCheckInMode("manual_supervisor_confirmed");
        event.setSupervisorConfirmedBy(actorId());
        AttendanceEventEntity saved = attendanceRepository.save(event);
        emit(tenantId, saved, "supervisor_confirmed");
        return new VashandiDtos.AttendanceActionResponse(saved.getId(), "completed", "supervisor confirmed");
    }

    private void emit(UUID tenantId, AttendanceEventEntity event, String action) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", event.getId().toString());
        payload.put("eventType", event.getEventType());
        outboxWriter.publish(tenantId, "ATTENDANCE", event.getId().toString(), "attendance", action,
                "vashandi:attendance:" + action + ":" + event.getId(), payload);
    }

    private String actorId() {
        try {
            return TrustContextHolder.require().actorId();
        } catch (IllegalStateException ex) {
            return "system";
        }
    }
}
