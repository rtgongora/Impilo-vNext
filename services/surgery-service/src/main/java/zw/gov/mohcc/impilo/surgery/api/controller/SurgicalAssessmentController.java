package zw.gov.mohcc.impilo.surgery.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.surgery.core.SurgicalAssessmentService;

import java.util.Map;
import java.util.UUID;

/**
 * S2 — general surgical assessment API. Backend-internal only, same deferred-reachability
 * shape as S1's own controller.
 */
@RestController
@RequestMapping("/internal/v1/surgery/episodes/{episodeId}/assessment")
public class SurgicalAssessmentController {

    private final SurgicalAssessmentService service;

    public SurgicalAssessmentController(SurgicalAssessmentService service) {
        this.service = service;
    }

    @PutMapping
    public Map<String, Object> record(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return service.recordAssessment(episodeId, body);
    }

    @GetMapping
    public Map<String, Object> get(@PathVariable UUID episodeId) {
        return service.getAssessment(episodeId);
    }
}
