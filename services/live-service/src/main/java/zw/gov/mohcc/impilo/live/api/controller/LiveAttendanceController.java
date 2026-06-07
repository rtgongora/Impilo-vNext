package zw.gov.mohcc.impilo.live.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.live.api.dto.LiveDtos;
import zw.gov.mohcc.impilo.live.core.AttendanceService;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventAttendanceEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/live/attendance")
public class LiveAttendanceController {

    private final AttendanceService attendanceService;

    public LiveAttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/{eventId}/join")
    public ResponseEntity<LiveDtos.AttendanceResponse> join(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(
                attendanceService.join(tenantId, eventId,
                        body.get("participantId"), body.getOrDefault("participantType", "PROVIDER"))));
    }

    @PostMapping("/{eventId}/leave")
    public LiveDtos.AttendanceResponse leave(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @RequestBody Map<String, String> body) {
        return toResponse(attendanceService.leave(tenantId, eventId, body.get("participantId")));
    }

    @PutMapping("/{eventId}/participants/{participantId}/minutes")
    public LiveDtos.AttendanceResponse trackMinutes(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @PathVariable String participantId,
            @RequestBody Map<String, Integer> body) {
        return toResponse(attendanceService.trackMinutes(tenantId, eventId, participantId,
                body.getOrDefault("liveMinutes", 0),
                body.getOrDefault("replayMinutes", 0)));
    }

    @GetMapping("/{eventId}")
    public List<LiveDtos.AttendanceResponse> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId) {
        return attendanceService.listForEvent(tenantId, eventId).stream()
                .map(this::toResponse).toList();
    }

    @GetMapping("/{eventId}/participants/{participantId}")
    public LiveDtos.AttendanceResponse get(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @PathVariable String participantId) {
        return toResponse(attendanceService.get(tenantId, eventId, participantId));
    }

    private LiveDtos.AttendanceResponse toResponse(LiveEventAttendanceEntity a) {
        return new LiveDtos.AttendanceResponse(
                a.getId(), a.getEventId(), a.getParticipantId(),
                a.getLiveWatchMinutes(), a.getTotalWatchMinutes(),
                a.isEligibleForCertificate(), a.isEligibleForCpd(), a.getCompletionStatus());
    }
}
