package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.workhome.ProfessionalAlertsComposer;
import zw.gov.mohcc.impilo.experience.workhome.WorkHomeSection;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Professional-standing alerts (Phase F4) for {@code /professional} — licence expiry/
 * suspension, outstanding CPD, assignments awaiting acceptance. Reuses
 * {@link ProfessionalAlertsComposer} (Phase E5) standalone, independent of any active work
 * context — a person checking their professional standing has no {@code context_id} to supply
 * and should see these alerts whether or not they currently have a work session open.
 */
@RestController
@RequestMapping("/internal/v1/professional")
public class ProfessionalAlertsController {

    private final ProfessionalAlertsComposer composer;

    public ProfessionalAlertsController(ProfessionalAlertsComposer composer) {
        this.composer = composer;
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> alerts(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId) {

        WorkHomeSection section = composer.compose(actorId);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("status", section.status());
        attributes.put("items", section.items());
        attributes.put("summary", section.summary());
        attributes.put("note", section.note());
        attributes.put("generatedAt", section.generatedAt());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", actorId, "type", "professional-alerts", "attributes", attributes));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "generated_at", Instant.now().toString()));
        return ResponseEntity.ok(response);
    }
}
