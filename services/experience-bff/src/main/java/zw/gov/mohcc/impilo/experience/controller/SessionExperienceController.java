package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.session.SessionExperienceService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Issues the Session Experience Contract for web/mobile shell rendering.
 * BFF enforces trust doctrine; OPA validates protected actions server-side.
 */
@RestController
@RequestMapping("/internal/v1/session")
public class SessionExperienceController {

    private final SessionExperienceService sessionExperienceService;

    public SessionExperienceController(SessionExperienceService sessionExperienceService) {
        this.sessionExperienceService = sessionExperienceService;
    }

    @GetMapping("/experience")
    public ResponseEntity<Map<String, Object>> getSessionExperience(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = "X-Login-Method", required = false) String loginMethod,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId) {

        boolean hasSelectedFacility = facilityId != null && !facilityId.isBlank();
        Map<String, Object> attributes = sessionExperienceService.buildExperienceContract(
                actorId, loginMethod, providerId, hasSelectedFacility);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", actorId,
                "type", "session-experience",
                "attributes", attributes
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
