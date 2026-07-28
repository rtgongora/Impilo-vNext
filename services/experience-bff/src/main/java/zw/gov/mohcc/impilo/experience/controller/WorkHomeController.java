package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.workhome.WorkHomeCompositionService;
import zw.gov.mohcc.impilo.experience.workhome.WorkHomeResult;
import zw.gov.mohcc.impilo.experience.workhome.WorkHomeSection;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The role/mode-aware Work Home (Phase E3). {@code GET /work-home} is the fat, first-paint
 * composition; {@code GET /work-home/sections/{id}} lets the shell retry or differentially
 * poll a single degraded section instead of re-fetching everything.
 *
 * <p>Always 200 when authenticated — a downstream failure degrades its own section
 * ({@link WorkHomeSection#status()}), it never turns into an HTTP 5xx for the whole page. A
 * context/mode that cannot be re-proven from source returns the same generic
 * {@code friendlyState} the C3 session mint uses, never an enumerated reason.</p>
 */
@RestController
@RequestMapping("/internal/v1/work-home")
public class WorkHomeController {

    private final WorkHomeCompositionService compositionService;

    public WorkHomeController(WorkHomeCompositionService compositionService) {
        this.compositionService = compositionService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> workHome(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestParam("context_id") String contextId,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false, name = "entry_route") String entryRoute) {

        WorkHomeResult result = compositionService.compose(actorId, entryRoute, contextId, mode);
        return ResponseEntity.ok(envelope(requestId, correlationId, actorId, result));
    }

    @GetMapping("/sections/{sectionId}")
    public ResponseEntity<Map<String, Object>> workHomeSection(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @PathVariable String sectionId,
            @RequestParam("context_id") String contextId,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false, name = "entry_route") String entryRoute) {

        WorkHomeSection section = compositionService.composeSection(actorId, entryRoute, contextId, mode, sectionId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", sectionId, "type", "work-home-section", "attributes", toSectionView(section)));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "generated_at", Instant.now().toString()));
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> envelope(String requestId, String correlationId, String actorId, WorkHomeResult result) {
        List<Map<String, Object>> sections = result.sections().stream().map(WorkHomeController::toSectionView).toList();

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("contextId", result.contextId());
        attributes.put("family", result.family());
        attributes.put("mode", result.mode());
        attributes.put("friendlyState", result.friendlyState() != null ? result.friendlyState() : "");
        attributes.put("sections", sections);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", actorId, "type", "work-home", "attributes", attributes));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "generated_at", Instant.now().toString()));
        return response;
    }

    private static Map<String, Object> toSectionView(WorkHomeSection section) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("sectionId", section.sectionId());
        v.put("title", section.title());
        v.put("status", section.status());
        v.put("items", section.items());
        v.put("buckets", section.buckets());
        v.put("summary", section.summary());
        v.put("note", section.note());
        v.put("generatedAt", section.generatedAt());
        return v;
    }
}
