package zw.gov.mohcc.impilo.surgery.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.surgery.core.PrehabItemService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SB-1 — prehabilitation/optimisation item API. Backend-internal only, same deferred-
 * reachability shape as this programme's other controllers.
 */
@RestController
@RequestMapping("/internal/v1/surgery/episodes/{episodeId}/prehab")
public class PrehabItemController {

    private final PrehabItemService service;

    public PrehabItemController(PrehabItemService service) {
        this.service = service;
    }

    @PutMapping
    public Map<String, Object> recordItem(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return service.recordItem(episodeId, body);
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID episodeId) {
        return service.listForEpisode(episodeId);
    }
}
