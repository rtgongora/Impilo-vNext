package zw.gov.mohcc.impilo.surgery.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.surgery.core.ComplicationPathwayService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SB-1 — complication pathway API. Backend-internal only, same deferred-reachability shape as
 * S1/S2/S3's own controllers.
 */
@RestController
@RequestMapping("/internal/v1/surgery/episodes/{episodeId}/complications")
public class ComplicationPathwayController {

    private final ComplicationPathwayService service;

    public ComplicationPathwayController(ComplicationPathwayService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> recognise(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return service.recognise(episodeId, body);
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID episodeId) {
        return service.listForEpisode(episodeId);
    }

    @PostMapping("/{pathwayId}/grade")
    public Map<String, Object> grade(@PathVariable UUID episodeId, @PathVariable UUID pathwayId,
                                     @RequestBody Map<String, Object> body) {
        return service.grade(pathwayId, body.get("gradeCode") != null ? body.get("gradeCode").toString() : null);
    }

    @PutMapping("/{pathwayId}")
    public Map<String, Object> update(@PathVariable UUID episodeId, @PathVariable UUID pathwayId,
                                      @RequestBody Map<String, Object> body) {
        return service.update(pathwayId, body);
    }

    @PostMapping("/{pathwayId}/disclose")
    public Map<String, Object> disclose(@PathVariable UUID episodeId, @PathVariable UUID pathwayId,
                                        @RequestBody Map<String, Object> body) {
        return service.disclose(pathwayId, body);
    }

    @PostMapping("/{pathwayId}/close")
    public Map<String, Object> close(@PathVariable UUID episodeId, @PathVariable UUID pathwayId,
                                     @RequestBody Map<String, Object> body) {
        return service.close(pathwayId, body);
    }
}
