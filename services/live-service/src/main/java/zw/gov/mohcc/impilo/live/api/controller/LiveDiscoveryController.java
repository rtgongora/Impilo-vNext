package zw.gov.mohcc.impilo.live.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.live.api.dto.LiveDtos;
import zw.gov.mohcc.impilo.live.core.LiveEventService;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/live/discover")
public class LiveDiscoveryController {

    private final LiveEventService eventService;

    public LiveDiscoveryController(LiveEventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/context/{contextType}")
    public List<LiveDtos.EventResponse> byContext(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable String contextType) {
        return eventService.discoverByContext(tenantId, contextType).stream()
                .map(this::toResponse).toList();
    }

    @GetMapping("/facility/{facilityId}")
    public List<LiveDtos.EventResponse> byFacility(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable String facilityId) {
        return eventService.discoverByFacility(tenantId, facilityId).stream()
                .map(this::toResponse).toList();
    }

    @GetMapping("/role")
    public List<LiveDtos.EventResponse> byRole(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam String userId,
            @RequestParam String role) {
        return eventService.discoverByRole(tenantId, userId, role).stream()
                .map(this::toResponse).toList();
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
