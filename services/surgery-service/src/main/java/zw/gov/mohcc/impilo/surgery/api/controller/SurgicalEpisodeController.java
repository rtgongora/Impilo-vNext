package zw.gov.mohcc.impilo.surgery.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.surgery.api.dto.SurgicalEpisodeDtos.OpenEpisodeRequest;
import zw.gov.mohcc.impilo.surgery.api.dto.SurgicalEpisodeDtos.ReopenEpisodeRequest;
import zw.gov.mohcc.impilo.surgery.api.dto.SurgicalEpisodeDtos.SurgicalEpisodeView;
import zw.gov.mohcc.impilo.surgery.core.EpisodeSpecialtyService;
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
    private final EpisodeSpecialtyService specialtyService;

    public SurgicalEpisodeController(SurgicalEpisodeService service, EpisodeSpecialtyService specialtyService) {
        this.service = service;
        this.specialtyService = specialtyService;
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

    // ── V011, demonstration 4: every specialty on the case, not just the lead ──────────

    @GetMapping("/{id}/specialties")
    public List<Map<String, Object>> specialties(@PathVariable UUID id) {
        return specialtyService.listSpecialties(id);
    }

    @PostMapping("/{id}/specialties")
    public Map<String, Object> addSpecialty(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return specialtyService.addSpecialty(id, body.get("specialty"), body.get("role"), body.get("contribution"));
    }

    /** Handing the lead over is a distinct act from adding a team, and has its own endpoint. */
    @PostMapping("/{id}/specialties/lead")
    public List<Map<String, Object>> transferLead(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return specialtyService.transferLead(id, body.get("specialty"), body.get("contribution"));
    }

    /**
     * The specialty travels as a QUERY PARAMETER, not a final path segment. As a segment it
     * would become the ext_authz derived resource type ({@code .../specialties/GENERAL_SURGERY}
     * derives {@code GENERAL_SURGERY}, not {@code specialties}), so no policy row could ever
     * match it — the free-text-code-in-path trap the programme was corrected for.
     */
    @DeleteMapping("/{id}/specialties")
    public List<Map<String, Object>> removeSpecialty(@PathVariable UUID id, @RequestParam String specialty) {
        return specialtyService.removeSpecialty(id, specialty);
    }
}
