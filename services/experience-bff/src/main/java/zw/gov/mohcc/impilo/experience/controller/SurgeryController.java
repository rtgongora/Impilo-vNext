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

    // ── Completion wave: reoperation (V010) and shared-specialty care (V011); gated by V303 ──

    @PostMapping(value = "/{episodeId}/reopen", consumes = "application/json")
    public ResponseEntity<String> reopen(@PathVariable UUID episodeId, @RequestBody String requestBody) {
        return surgery.reopen(episodeId, requestBody);
    }

    @GetMapping("/{episodeId}/specialties")
    public ResponseEntity<String> specialties(@PathVariable UUID episodeId) {
        return surgery.specialties(episodeId);
    }

    @PostMapping(value = "/{episodeId}/specialties", consumes = "application/json")
    public ResponseEntity<String> addSpecialty(@PathVariable UUID episodeId, @RequestBody String requestBody) {
        return surgery.addSpecialty(episodeId, requestBody);
    }

    @PostMapping(value = "/{episodeId}/specialties/lead", consumes = "application/json")
    public ResponseEntity<String> transferLead(@PathVariable UUID episodeId, @RequestBody String requestBody) {
        return surgery.transferLead(episodeId, requestBody);
    }

    /** {@code ?specialty=} rather than a path segment — see {@link SurgeryServiceClient#removeSpecialty}. */
    @DeleteMapping("/{episodeId}/specialties")
    public ResponseEntity<String> removeSpecialty(@PathVariable UUID episodeId, @RequestParam String specialty) {
        return surgery.removeSpecialty(episodeId, specialty);
    }

    // ── Course-of-care (parity Wave A); gated by V304 ──

    @PutMapping(value = "/{episodeId}/prehab", consumes = "application/json")
    public ResponseEntity<String> recordPrehab(@PathVariable UUID episodeId, @RequestBody String requestBody) {
        return surgery.recordPrehab(episodeId, requestBody);
    }

    @GetMapping("/{episodeId}/prehab")
    public ResponseEntity<String> prehab(@PathVariable UUID episodeId) {
        return surgery.prehab(episodeId);
    }

    @PostMapping(value = "/{episodeId}/complications", consumes = "application/json")
    public ResponseEntity<String> recogniseComplication(@PathVariable UUID episodeId,
                                                        @RequestBody String requestBody) {
        return surgery.recogniseComplication(episodeId, requestBody);
    }

    @GetMapping("/{episodeId}/complications")
    public ResponseEntity<String> complications(@PathVariable UUID episodeId) {
        return surgery.complications(episodeId);
    }

    @PutMapping(value = "/{episodeId}/complications/{pathwayId}", consumes = "application/json")
    public ResponseEntity<String> updateComplication(@PathVariable UUID episodeId,
                                                     @PathVariable UUID pathwayId,
                                                     @RequestBody String requestBody) {
        return surgery.updateComplication(episodeId, pathwayId, requestBody);
    }

    @PostMapping(value = "/{episodeId}/complications/{pathwayId}/grade", consumes = "application/json")
    public ResponseEntity<String> gradeComplication(@PathVariable UUID episodeId,
                                                    @PathVariable UUID pathwayId,
                                                    @RequestBody String requestBody) {
        return surgery.gradeComplication(episodeId, pathwayId, requestBody);
    }

    @PostMapping(value = "/{episodeId}/complications/{pathwayId}/disclose", consumes = "application/json")
    public ResponseEntity<String> discloseComplication(@PathVariable UUID episodeId,
                                                       @PathVariable UUID pathwayId,
                                                       @RequestBody String requestBody) {
        return surgery.discloseComplication(episodeId, pathwayId, requestBody);
    }

    @PostMapping(value = "/{episodeId}/complications/{pathwayId}/close", consumes = "application/json")
    public ResponseEntity<String> closeComplication(@PathVariable UUID episodeId,
                                                    @PathVariable UUID pathwayId,
                                                    @RequestBody String requestBody) {
        return surgery.closeComplication(episodeId, pathwayId, requestBody);
    }

    @PostMapping(value = "/{episodeId}/longitudinal-objects", consumes = "application/json")
    public ResponseEntity<String> placeLongitudinalObject(@PathVariable UUID episodeId,
                                                          @RequestBody String requestBody) {
        return surgery.placeLongitudinalObject(episodeId, requestBody);
    }

    @GetMapping("/{episodeId}/longitudinal-objects")
    public ResponseEntity<String> longitudinalObjects(@PathVariable UUID episodeId) {
        return surgery.longitudinalObjects(episodeId);
    }

    @PostMapping(value = "/{episodeId}/longitudinal-objects/{objectId}/remove",
            consumes = "application/json")
    public ResponseEntity<String> removeLongitudinalObject(@PathVariable UUID episodeId,
                                                           @PathVariable UUID objectId,
                                                           @RequestBody(required = false) String requestBody) {
        return surgery.removeLongitudinalObject(episodeId, objectId, requestBody);
    }

    @PostMapping(value = "/{episodeId}/longitudinal-objects/{objectId}/revise",
            consumes = "application/json")
    public ResponseEntity<String> reviseLongitudinalObject(@PathVariable UUID episodeId,
                                                           @PathVariable UUID objectId,
                                                           @RequestBody(required = false) String requestBody) {
        return surgery.reviseLongitudinalObject(episodeId, objectId, requestBody);
    }

    @PutMapping(value = "/{episodeId}/followup", consumes = "application/json")
    public ResponseEntity<String> recordFollowup(@PathVariable UUID episodeId,
                                                 @RequestBody String requestBody) {
        return surgery.recordFollowup(episodeId, requestBody);
    }

    @GetMapping("/{episodeId}/followup")
    public ResponseEntity<String> followup(@PathVariable UUID episodeId) {
        return surgery.followup(episodeId);
    }

    @PostMapping(value = "/{episodeId}/waitlist-revalidation", consumes = "application/json")
    public ResponseEntity<String> revalidateWaitlist(@PathVariable UUID episodeId,
                                                     @RequestBody String requestBody) {
        return surgery.revalidateWaitlist(episodeId, requestBody);
    }

    @GetMapping("/{episodeId}/waitlist-revalidation")
    public ResponseEntity<String> waitlistRevalidations(@PathVariable UUID episodeId) {
        return surgery.waitlistRevalidations(episodeId);
    }
}
