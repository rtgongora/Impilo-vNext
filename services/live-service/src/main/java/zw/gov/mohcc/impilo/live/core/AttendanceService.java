package zw.gov.mohcc.impilo.live.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventAttendanceEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventAttendanceRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AttendanceService {

    private final LiveEventService eventService;
    private final LiveEventAttendanceRepository attendanceRepository;
    private final LiveEventEmitter emitter;

    public AttendanceService(LiveEventService eventService,
                             LiveEventAttendanceRepository attendanceRepository,
                             LiveEventEmitter emitter) {
        this.eventService = eventService;
        this.attendanceRepository = attendanceRepository;
        this.emitter = emitter;
    }

    @Transactional
    public LiveEventAttendanceEntity join(UUID tenantId, UUID eventId,
                                          String participantId, String participantType) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        LiveEventAttendanceEntity attendance = attendanceRepository
                .findByEventIdAndParticipantId(eventId, participantId)
                .orElseGet(LiveEventAttendanceEntity::new);
        attendance.setEventId(eventId);
        attendance.setParticipantId(participantId);
        attendance.setParticipantType(participantType);
        if (attendance.getJoinedAt() == null) {
            attendance.setJoinedAt(OffsetDateTime.now());
        }
        attendance.setLeftAt(null);
        attendance.setCompletionStatus("IN_PROGRESS");
        LiveEventAttendanceEntity saved = attendanceRepository.save(attendance);
        emit(saved, event, "impilo.live.attendance.joined.v1");
        return saved;
    }

    @Transactional
    public LiveEventAttendanceEntity leave(UUID tenantId, UUID eventId, String participantId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        LiveEventAttendanceEntity attendance = attendanceRepository
                .findByEventIdAndParticipantId(eventId, participantId)
                .orElseThrow(() -> new IllegalArgumentException("Attendance not found"));
        OffsetDateTime now = OffsetDateTime.now();
        attendance.setLeftAt(now);
        if (attendance.getJoinedAt() != null) {
            int minutes = (int) Duration.between(attendance.getJoinedAt(), now).toMinutes();
            attendance.setLiveWatchMinutes(attendance.getLiveWatchMinutes() + minutes);
            attendance.setTotalWatchMinutes(attendance.getLiveWatchMinutes() + attendance.getReplayWatchMinutes());
        }
        evaluateEligibility(attendance, event);
        LiveEventAttendanceEntity saved = attendanceRepository.save(attendance);
        emit(saved, event, "impilo.live.attendance.left.v1");
        return saved;
    }

    @Transactional
    public LiveEventAttendanceEntity trackMinutes(UUID tenantId, UUID eventId, String participantId,
                                                  int liveMinutes, int replayMinutes) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        LiveEventAttendanceEntity attendance = attendanceRepository
                .findByEventIdAndParticipantId(eventId, participantId)
                .orElseThrow(() -> new IllegalArgumentException("Attendance not found"));
        attendance.setLiveWatchMinutes(liveMinutes);
        attendance.setReplayWatchMinutes(replayMinutes);
        attendance.setTotalWatchMinutes(liveMinutes + replayMinutes);
        evaluateEligibility(attendance, event);
        return attendanceRepository.save(attendance);
    }

    public void evaluateEligibility(LiveEventAttendanceEntity attendance, LiveEventEntity event) {
        int threshold = event.getAttendanceThresholdMinutes() != null
                ? event.getAttendanceThresholdMinutes() : 30;
        boolean meetsThreshold = attendance.getLiveWatchMinutes() >= threshold
                || attendance.getTotalWatchMinutes() >= threshold;
        attendance.setEligibleForCertificate(meetsThreshold);
        attendance.setEligibleForCpd(event.isCpdEnabled() && meetsThreshold);
        if (meetsThreshold) {
            attendance.setCompletionStatus("COMPLETED");
        }
    }

    @Transactional(readOnly = true)
    public List<LiveEventAttendanceEntity> listForEvent(UUID tenantId, UUID eventId) {
        eventService.get(tenantId, eventId);
        return attendanceRepository.findByEventId(eventId);
    }

    @Transactional(readOnly = true)
    public LiveEventAttendanceEntity get(UUID tenantId, UUID eventId, String participantId) {
        eventService.get(tenantId, eventId);
        return attendanceRepository.findByEventIdAndParticipantId(eventId, participantId)
                .orElseThrow(() -> new IllegalArgumentException("Attendance not found"));
    }

    private void emit(LiveEventAttendanceEntity attendance, LiveEventEntity event, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attendanceId", attendance.getId().toString());
        payload.put("eventId", attendance.getEventId().toString());
        payload.put("participantId", attendance.getParticipantId());
        payload.put("liveWatchMinutes", attendance.getLiveWatchMinutes());
        payload.put("eligibleForCpd", attendance.isEligibleForCpd());
        payload.put("eligibleForCertificate", attendance.isEligibleForCertificate());
        emitter.emit(event.getTenantId(), "LIVE_ATTENDANCE", attendance.getId().toString(),
                eventType, "LIVE_EVENT", attendance.getEventId().toString(), payload);
    }
}
