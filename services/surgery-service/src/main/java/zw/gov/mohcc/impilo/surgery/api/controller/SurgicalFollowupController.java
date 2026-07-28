package zw.gov.mohcc.impilo.surgery.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.surgery.core.SurgicalFollowupService;

import java.util.Map;
import java.util.UUID;

/** SB-2 — discharge/follow-up API. Backend-internal only, deferred-reachability shape. */
@RestController
@RequestMapping("/internal/v1/surgery/episodes/{episodeId}/followup")
public class SurgicalFollowupController {

    private final SurgicalFollowupService service;

    public SurgicalFollowupController(SurgicalFollowupService service) {
        this.service = service;
    }

    @PutMapping
    public Map<String, Object> record(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return service.record(episodeId, body);
    }

    @GetMapping
    public Map<String, Object> get(@PathVariable UUID episodeId) {
        return service.get(episodeId);
    }
}
