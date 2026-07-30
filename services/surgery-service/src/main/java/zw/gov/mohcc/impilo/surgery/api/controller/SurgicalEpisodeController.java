package zw.gov.mohcc.impilo.surgery.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.surgery.api.dto.SurgicalEpisodeDtos.OpenEpisodeRequest;
import zw.gov.mohcc.impilo.surgery.api.dto.SurgicalEpisodeDtos.ReopenEpisodeRequest;
import zw.gov.mohcc.impilo.surgery.api.dto.SurgicalEpisodeDtos.SurgicalEpisodeView;
import zw.gov.mohcc.impilo.surgery.core.SurgicalEpisodeService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S1 — surgical episode API. Backend-internal only this wave, same shape as P0's own
 * equivalent in the sibling {@code procedures-service} programme: no authz/BFF/UI wiring yet,
 * because this IS the first business endpoint to authorize or proxy. Deferred to a dedicated
 * reachability wave, not attempted inline with content, mirroring P7-P12's own precedent.
 *
 * <p>Episode ids travel as a PATH VARIABLE deliberately — they are opaque UUIDs, not free-text
 * codes, so P-R.4's `deriveResourceType`/UUID-segment trap does not apply here (it is the
 * free-text-code-as-final-segment shape that defeats it, not any path variable).</p>
 */
@RestController
@RequestMapping("/internal/v1/surgery/episodes")
public class SurgicalEpisodeController {

    private final SurgicalEpisodeService service;

    public SurgicalEpisodeController(SurgicalEpisodeService service) {
        this.service = service;
    }

    @PostMapping
    public SurgicalEpisodeView open(@RequestBody OpenEpisodeRequest req) {
        return service.openEpisode(req);
    }

    @GetMapping("/{id}")
    public SurgicalEpisodeView get(@PathVariable UUID id) {
        return service.getEpisode(id);
    }

    @GetMapping
    public List<SurgicalEpisodeView> forSubject(@RequestParam String subjectCpid) {
        return service.episodesForSubject(subjectCpid);
    }

    @PostMapping("/{id}/link-procedure-episode")
    public SurgicalEpisodeView linkProcedureEpisode(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return service.linkProcedureEpisode(id, UUID.fromString(body.get("procedureEpisodeRef")));
    }

    @PostMapping("/{id}/transition")
    public SurgicalEpisodeView transition(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return service.transition(id, body.get("status"));
    }

    /**
     * Reopen for a return to theatre (V010, demonstration 9). Separate from {@code /transition}
     * because it carries a mandatory reason and records who reopened the episode.
     */
    @PostMapping("/{id}/reopen")
    public SurgicalEpisodeView reopen(@PathVariable UUID id, @RequestBody ReopenEpisodeRequest req) {
        UUID predecessor = null;
        if (req.reoperationOfEpisodeId() != null && !req.reoperationOfEpisodeId().isBlank()) {
            predecessor = UUID.fromString(req.reoperationOfEpisodeId());
        }
        return service.reopen(id, req.reason(), predecessor);
    }
}
