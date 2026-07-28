package zw.gov.mohcc.impilo.surgery.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.surgery.core.LongitudinalObjectService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SB-2 — drain/stoma/wound API. Backend-internal only, deferred-reachability shape. */
@RestController
@RequestMapping("/internal/v1/surgery/episodes/{episodeId}/longitudinal-objects")
public class LongitudinalObjectController {

    private final LongitudinalObjectService service;

    public LongitudinalObjectController(LongitudinalObjectService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> place(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return service.place(episodeId, body);
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID episodeId) {
        return service.listForEpisode(episodeId);
    }

    @PostMapping("/{objectId}/remove")
    public Map<String, Object> remove(@PathVariable UUID episodeId, @PathVariable UUID objectId,
                                      @RequestBody(required = false) Map<String, Object> body) {
        return service.remove(objectId, body != null ? body : Map.of());
    }

    @PostMapping("/{objectId}/revise")
    public Map<String, Object> revise(@PathVariable UUID episodeId, @PathVariable UUID objectId,
                                      @RequestBody(required = false) Map<String, Object> body) {
        return service.revise(objectId, body != null ? body : Map.of());
    }
}
