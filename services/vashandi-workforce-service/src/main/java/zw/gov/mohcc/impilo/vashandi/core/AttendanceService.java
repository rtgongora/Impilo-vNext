package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AttendanceEventEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ShiftEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.AttendanceEventRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftRepository;
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
    private final VashandiOutboxWriter outboxWriter;

    public AttendanceService(AttendanceEventRepository attendanceRepository,
                             ShiftRepository shiftRepository,
                             VashandiOutboxWriter outboxWriter) {
        this.attendanceRepository = attendanceRepository;
        this.shiftRepository = shiftRepository;
        this.outboxWriter = outboxWriter;
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

        AttendanceEventEntity event = new AttendanceEventEntity();
        event.setTenantId(tenantId);
        event.setShiftId(request.shiftId());
        event.setWorkforceProfileId(request.workforceProfileId());
        event.setEventType("check_in");
        event.setEventTime(request.eventTime() != null ? request.eventTime() : OffsetDateTime.now());
        event.setCheckInMode(request.checkInMode() != null ? request.checkInMode() : "self_check_in");
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
