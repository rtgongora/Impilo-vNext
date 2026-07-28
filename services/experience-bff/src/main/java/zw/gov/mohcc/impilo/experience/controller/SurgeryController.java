package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.SurgeryServiceClient;

import java.util.UUID;

/**
 * Experience-BFF facade for surgery-service — the surgical episode spine (S1), general
 * surgical assessment (S2) and surgical decision-making record (S3). Composition/orchestration
 * only; surgery-service is the SoR for these rows and every response is forwarded verbatim.
 *
 * <p>Every route stays under {@code /internal/v1/surgery/**}, which envoy already routes
 * generically to the experience_bff cluster — no envoy change was needed (same programme
 * finding as the procedures proxy, P-R.5). tshepo-authz V302 gates each route at the
 * ext_authz edge; episode ids are UUID path variables deliberately, because the PDP's
 * resource-type derivation skips UUID segments (fixed words remain the derived type — proven
 * in SurgeryReachabilityRouteShapeTest, not assumed).</p>
 *
 * <p><b>Failure never renders as empty.</b> No try/catch here: a downstream 5xx or connection
 * failure from {@link SurgeryServiceClient} propagates to {@code BffGlobalExceptionHandler}
 * and returns as a genuine non-2xx with a structured error body — never masked into a 200 with
 * an empty payload. The React Query hooks consuming this controller
 * ({@code useSurgeryEpisodes.ts}) hold the client-side half of that guarantee.</p>
 */
@RestController
@RequestMapping("/internal/v1/surgery/episodes")
public class SurgeryController {

    private final SurgeryServiceClient surgery;

    public SurgeryController(SurgeryServiceClient surgery) {
        this.surgery = surgery;
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<String> openEpisode(@RequestBody String requestBody) {
        return surgery.openEpisode(requestBody);
    }

    @GetMapping
    public ResponseEntity<String> episodesForSubject(@RequestParam String subjectCpid) {
        return surgery.episodesForSubject(subjectCpid);
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> episode(@PathVariable UUID id) {
        return surgery.episode(id);
    }

    @PostMapping(value = "/{id}/link-procedure-episode", consumes = "application/json")
    public ResponseEntity<String> linkProcedureEpisode(@PathVariable UUID id, @RequestBody String requestBody) {
        return surgery.linkProcedureEpisode(id, requestBody);
    }

    @PostMapping(value = "/{id}/transition", consumes = "application/json")
    public ResponseEntity<String> transition(@PathVariable UUID id, @RequestBody String requestBody) {
        return surgery.transition(id, requestBody);
    }

    @PutMapping(value = "/{episodeId}/assessment", consumes = "application/json")
    public ResponseEntity<String> recordAssessment(@PathVariable UUID episodeId, @RequestBody String requestBody) {
        return surgery.recordAssessment(episodeId, requestBody);
    }

    @GetMapping("/{episodeId}/assessment")
    public ResponseEntity<String> assessment(@PathVariable UUID episodeId) {
        return surgery.assessment(episodeId);
    }

    @PutMapping(value = "/{episodeId}/decision", consumes = "application/json")
    public ResponseEntity<String> recordDecision(@PathVariable UUID episodeId, @RequestBody String requestBody) {
        return surgery.recordDecision(episodeId, requestBody);
    }

    @GetMapping("/{episodeId}/decision")
    public ResponseEntity<String> decision(@PathVariable UUID episodeId) {
        return surgery.decision(episodeId);
    }
}
