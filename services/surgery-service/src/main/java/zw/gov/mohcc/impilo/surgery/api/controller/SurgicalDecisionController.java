package zw.gov.mohcc.impilo.surgery.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.surgery.core.SurgicalDecisionService;

import java.util.Map;
import java.util.UUID;

/**
 * S3 — surgical decision-making record API. Backend-internal only, same deferred-reachability
 * shape as S1/S2's own controllers.
 */
@RestController
@RequestMapping("/internal/v1/surgery/episodes/{episodeId}/decision")
public class SurgicalDecisionController {

    private final SurgicalDecisionService service;

    public SurgicalDecisionController(SurgicalDecisionService service) {
        this.service = service;
    }

    @PutMapping
    public Map<String, Object> record(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return service.recordDecision(episodeId, body);
    }

    @GetMapping
    public Map<String, Object> get(@PathVariable UUID episodeId) {
        return service.getDecision(episodeId);
    }
}
