package zw.gov.mohcc.impilo.live.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.live.api.dto.LiveDtos;
import zw.gov.mohcc.impilo.live.core.RegistrationService;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventRegistrationEntity;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/live/registrations")
public class LiveRegistrationController {

    private final RegistrationService registrationService;

    public LiveRegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/events/{eventId}")
    public ResponseEntity<LiveDtos.RegistrationResponse> register(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @RequestBody LiveDtos.RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(
                registrationService.register(tenantId, eventId, req.participantId(),
                        req.participantType(), req.inviteSource())));
    }

    @DeleteMapping("/events/{eventId}/participants/{participantId}")
    public LiveDtos.RegistrationResponse cancel(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @PathVariable String participantId) {
        return toResponse(registrationService.cancel(tenantId, eventId, participantId));
    }

    @PutMapping("/events/{eventId}/participants/{participantId}/saved")
    public LiveDtos.RegistrationResponse save(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @PathVariable String participantId,
            @RequestParam boolean saved) {
        return toResponse(registrationService.saveBookmark(tenantId, eventId, participantId, saved));
    }

    @GetMapping("/events/{eventId}")
    public List<LiveDtos.RegistrationResponse> listForEvent(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId) {
        return registrationService.listForEvent(tenantId, eventId).stream()
                .map(this::toResponse).toList();
    }

    @GetMapping("/participants/{participantId}")
    public List<LiveDtos.RegistrationResponse> listForParticipant(@PathVariable String participantId) {
        return registrationService.listForParticipant(participantId).stream()
                .map(this::toResponse).toList();
    }

    private LiveDtos.RegistrationResponse toResponse(LiveEventRegistrationEntity r) {
        return new LiveDtos.RegistrationResponse(
                r.getId(), r.getEventId(), r.getParticipantId(),
                r.getRegistrationStatus(), r.isSaved(), r.getRegisteredAt());
    }
}
