package zw.gov.mohcc.impilo.live.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.live.api.dto.LiveDtos;
import zw.gov.mohcc.impilo.live.core.LiveEventService;
import zw.gov.mohcc.impilo.live.core.LiveIntegrationOrchestrator;
import zw.gov.mohcc.impilo.live.domain.LiveEventStatus;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/live/events")
public class LiveEventController {

    private final LiveEventService eventService;
    private final LiveIntegrationOrchestrator integrationOrchestrator;

    public LiveEventController(LiveEventService eventService,
                               LiveIntegrationOrchestrator integrationOrchestrator) {
        this.eventService = eventService;
        this.integrationOrchestrator = integrationOrchestrator;
    }

    @PostMapping
    public ResponseEntity<LiveDtos.EventResponse> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody LiveDtos.CreateEventRequest req) {
        LiveEventEntity entity = new LiveEventEntity();
        entity.setTenantId(tenantId);
        entity.setTitle(req.title());
        entity.setDescription(req.description());
        entity.setMode(req.mode());
        entity.setOwningService(req.owningService());
        entity.setOwningEntityId(req.owningEntityId());
        entity.setEventType(req.eventType());
        entity.setContextType(req.contextType() != null ? req.contextType() : "PROFESSIONAL");
        entity.setAudienceType(req.audienceType() != null ? req.audienceType() : "ROLE_BASED");
        entity.setVisibility(req.visibility() != null ? req.visibility() : "PRIVATE");
        entity.setStatus(LiveEventStatus.DRAFT.name());
        entity.setOrganiserType(req.organiserType());
        entity.setOrganiserId(req.organiserId());
        entity.setFacilityId(req.facilityId());
        entity.setDistrictId(req.districtId());
        entity.setProvinceId(req.provinceId());
        entity.setProgrammeId(req.programmeId());
        entity.setStartTime(req.startTime());
        entity.setEndTime(req.endTime());
        if (req.registrationRequired() != null) entity.setRegistrationRequired(req.registrationRequired());
        entity.setMaxParticipants(req.maxParticipants());
        if (req.recordingAllowed() != null) entity.setRecordingAllowed(req.recordingAllowed());
        if (req.replayAllowed() != null) entity.setReplayAllowed(req.replayAllowed());
        if (req.cpdEnabled() != null) entity.setCpdEnabled(req.cpdEnabled());
        entity.setCpdPoints(req.cpdPoints());
        entity.setAttendanceThresholdMinutes(req.attendanceThresholdMinutes());
        entity.setAgenda(req.agenda());
        entity.setObjectives(req.objectives());
        entity.setLinkedFundoCourseId(req.linkedFundoCourseId());
        entity.setLinkedMadiDriveId(req.linkedMadiDriveId());
        entity.setCreatedBy(actorId != null ? actorId : req.organiserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(eventService.create(entity)));
    }

    @GetMapping("/{eventId}")
    public LiveDtos.EventResponse get(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId) {
        return toResponse(eventService.get(tenantId, eventId));
    }

    @PutMapping("/{eventId}")
    public LiveDtos.EventResponse update(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID eventId,
            @RequestBody LiveEventEntity updates) {
        return toResponse(eventService.update(tenantId, eventId, updates,
                actorId != null ? actorId : "system"));
    }

    @PostMapping("/{eventId}/schedule")
    public LiveDtos.EventResponse schedule(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID eventId) {
        return toResponse(eventService.schedule(tenantId, eventId,
                actorId != null ? actorId : "system"));
    }

    @PostMapping("/{eventId}/cancel")
    public LiveDtos.EventResponse cancel(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID eventId) {
        return toResponse(eventService.cancel(tenantId, eventId,
                actorId != null ? actorId : "system"));
    }

    @GetMapping("/{eventId}/madi-drive")
    public Map<String, Object> madiDrive(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        return integrationOrchestrator.resolveMadiDrive(event);
    }

    @GetMapping
    public List<LiveDtos.EventResponse> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(required = false) String status) {
        List<LiveEventEntity> events = status != null
                ? eventService.listByStatus(tenantId, status)
                : eventService.listByStatus(tenantId, LiveEventStatus.SCHEDULED.name());
        return events.stream().map(this::toResponse).toList();
    }

    private LiveDtos.EventResponse toResponse(LiveEventEntity e) {
        return new LiveDtos.EventResponse(
                e.getId(), e.getTenantId(), e.getTitle(), e.getDescription(),
                e.getMode(), e.getOwningService(), e.getOwningEntityId(),
                e.getEventType(), e.getContextType(), e.getStatus(), e.getFacilityId(),
                e.getStartTime(), e.getEndTime(), e.isCpdEnabled(), e.getCpdPoints(),
                e.getAttendanceThresholdMinutes(), e.isChatEnabled(), e.isQnaEnabled(),
                e.isPollsEnabled());
    }
}
