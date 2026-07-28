package zw.gov.mohcc.impilo.surgery.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.surgery.core.WaitlistRevalidationService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SB-2 — waiting-list clinical revalidation API. Backend-internal only. */
@RestController
@RequestMapping("/internal/v1/surgery/episodes/{episodeId}/waitlist-revalidation")
public class WaitlistRevalidationController {

    private final WaitlistRevalidationService service;

    public WaitlistRevalidationController(WaitlistRevalidationService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> revalidate(@PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        return service.revalidate(episodeId, body);
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID episodeId) {
        return service.listForEpisode(episodeId);
    }
}
