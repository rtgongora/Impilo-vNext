package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.service.SubjectResolutionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only proxy for pct's confidential reproductive-health lane
 * ({@code ConfidentialReproductiveController}): contraception coverage and history, pregnancy
 * losses, TOP authorisations, and termination procedures.
 *
 * <h2>Why {@code /internal/v1/confidential/reproductive} and not, say, {@code /internal/v1/reproductive}</h2>
 * <p>tshepo-authz classifies confidentiality from the path THE CLIENT CALLS, which for a web or
 * mobile caller is this BFF path — not the pct path behind it. A BFF route mounted off the
 * confidential lane gets no {@code confidentialCategories} minted for it, so once obligation
 * propagation is enabled the BFF forwards an obligation carrying no grant and pct's fail-closed
 * guard withholds every stamped record from every requester, including the woman whose record it
 * is. {@code scripts/guard/check-confidential-lane-routing.sh} makes that a build failure — see
 * {@link ConfidentialMaternityController} for the sibling lane this mirrors.
 *
 * <h2>A withheld record is answered exactly like one that does not exist</h2>
 * <p>pct's guard returns an absent row rather than a 403 for a record the requester may not see —
 * a distinguishable 403 would tell a reader the record IS there, which is most of what
 * confidentiality was protecting. This proxy preserves that: an unresolved subject, and any list
 * pct answers as empty, both come back as a 200 with an empty list or null body, never a 404.
 *
 * <h2>Reads and writes on the confidential reproductive lane</h2>
 * <p>W14-B adds reproductive intention, preconception, fertility and delivery surfaces alongside
 * the W13-B contraception, loss and TOP reads. Writes pass domain statuses through verbatim; only
 * transport failure becomes a 502.
 *
 * <h2>The browser holds neither HID nor CPID (Identity Contract §12)</h2>
 * <p>{@code {subjectCpid}} / {@code {motherCpid}} exist as real path segments so a clinician who
 * already resolved a patient's CPID can call this lane directly — but a citizen calling for
 * herself never learns her own CPID. She sends the sentinel {@code "me"}, and
 * {@link #resolveSubject} resolves it server-side from her authenticated actor identity via
 * {@link SubjectResolutionService}, exactly as {@link ConfidentialMaternityController} does.
 */
@RestController
@RequestMapping("/internal/v1/confidential/reproductive")
public class ConfidentialReproductiveReadController {

    private static final Logger log = LoggerFactory.getLogger(ConfidentialReproductiveReadController.class);

    private final PctServiceClient pct;
    private final SubjectResolutionService subjectResolution;

    public ConfidentialReproductiveReadController(PctServiceClient pct,
                                                  SubjectResolutionService subjectResolution) {
        this.pct = pct;
        this.subjectResolution = subjectResolution;
    }

    /**
     * The methods she is currently using, with coverage resolved as at a date.
     *
     * <p>{@code subjectCpid} is the sentinel {@code "me"} for a citizen reading her own record.
     * {@code asOf} is optional and passed through unchanged; pct decides what "today" means.
     */
    @GetMapping("/contraception/{subjectCpid}")
    public ResponseEntity<?> contraception(
            @PathVariable String subjectCpid,
            @RequestParam(required = false) String asOf,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String cpid = resolveSubject(subjectCpid, actorId);
        if (cpid == null) {
            return emptyList(requestId, correlationId);
        }
        return forward(() -> pct.getContraceptionCoverage(cpid, asOf), "contraception coverage",
                requestId, correlationId);
    }

    /** Every contraceptive method she has ever been recorded on. {@code subjectCpid} may be {@code "me"}. */
    @GetMapping("/contraception/{subjectCpid}/history")
    public ResponseEntity<?> contraceptionHistory(
            @PathVariable String subjectCpid,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String cpid = resolveSubject(subjectCpid, actorId);
        if (cpid == null) {
            return emptyList(requestId, correlationId);
        }
        return forward(() -> pct.getContraceptionHistory(cpid), "contraception history",
                requestId, correlationId);
    }

    /**
     * Pregnancy losses for a mother. Mother-anchored: a loss mints no person, so
     * {@code {motherCpid}} may be {@code "me"} for a woman reading her own record.
     */
    @GetMapping("/losses/{motherCpid}")
    public ResponseEntity<?> losses(
            @PathVariable String motherCpid,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String cpid = resolveSubject(motherCpid, actorId);
        if (cpid == null) {
            return emptyList(requestId, correlationId);
        }
        return forward(() -> pct.getPregnancyLosses(cpid), "pregnancy losses",
                requestId, correlationId);
    }

    /**
     * Termination authorisations for a subject, including requests that were declined.
     * {@code subjectCpid} may be {@code "me"}.
     */
    @GetMapping("/top-authorisations/{subjectCpid}")
    public ResponseEntity<?> topAuthorisations(
            @PathVariable String subjectCpid,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String cpid = resolveSubject(subjectCpid, actorId);
        if (cpid == null) {
            return emptyList(requestId, correlationId);
        }
        return forward(() -> pct.getTopAuthorisations(cpid), "TOP authorisations",
                requestId, correlationId);
    }

    /** Termination procedures for a subject. {@code subjectCpid} may be {@code "me"}. */
    @GetMapping("/terminations/{subjectCpid}")
    public ResponseEntity<?> terminations(
            @PathVariable String subjectCpid,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String cpid = resolveSubject(subjectCpid, actorId);
        if (cpid == null) {
            return emptyList(requestId, correlationId);
        }
        return forward(() -> pct.getTerminations(cpid), "termination procedures",
                requestId, correlationId);
    }

    /** Her current stated reproductive intention. {@code subjectCpid} may be {@code "me"}. */
    @GetMapping("/reproductive-intentions/{subjectCpid}/current")
    public ResponseEntity<?> currentReproductiveIntention(
            @PathVariable String subjectCpid,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String cpid = resolveSubject(subjectCpid, actorId);
        if (cpid == null) {
            return emptyObject(requestId, correlationId);
        }
        return forward(() -> pct.getCurrentReproductiveIntention(cpid), "reproductive intention",
                requestId, correlationId);
    }

    /** Reproductive intention history. {@code subjectCpid} may be {@code "me"}. */
    @GetMapping("/reproductive-intentions/{subjectCpid}/history")
    public ResponseEntity<?> reproductiveIntentionHistory(
            @PathVariable String subjectCpid,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String cpid = resolveSubject(subjectCpid, actorId);
        if (cpid == null) {
            return emptyList(requestId, correlationId);
        }
        return forward(() -> pct.getReproductiveIntentionHistory(cpid), "reproductive intention history",
                requestId, correlationId);
    }

    /** Active preconception plan. {@code subjectCpid} may be {@code "me"}. */
    @GetMapping("/preconception-plans/{subjectCpid}/active")
    public ResponseEntity<?> activePreconceptionPlan(
            @PathVariable String subjectCpid,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String cpid = resolveSubject(subjectCpid, actorId);
        if (cpid == null) {
            return emptyObject(requestId, correlationId);
        }
        return forward(() -> pct.getActivePreconceptionPlan(cpid), "preconception plan",
                requestId, correlationId);
    }

    /** Preconception plan history. {@code subjectCpid} may be {@code "me"}. */
    @GetMapping("/preconception-plans/{subjectCpid}/history")
    public ResponseEntity<?> preconceptionPlanHistory(
            @PathVariable String subjectCpid,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String cpid = resolveSubject(subjectCpid, actorId);
        if (cpid == null) {
            return emptyList(requestId, correlationId);
        }
        return forward(() -> pct.getPreconceptionPlanHistory(cpid), "preconception plan history",
                requestId, correlationId);
    }

    /** Open fertility episode. {@code subjectCpid} may be {@code "me"}. */
    @GetMapping("/fertility-episodes/{subjectCpid}/current")
    public ResponseEntity<?> currentFertilityEpisode(
            @PathVariable String subjectCpid,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String cpid = resolveSubject(subjectCpid, actorId);
        if (cpid == null) {
            return emptyObject(requestId, correlationId);
        }
        return forward(() -> pct.getCurrentFertilityEpisode(cpid), "fertility episode",
                requestId, correlationId);
    }

    /** Fertility episode history. {@code subjectCpid} may be {@code "me"}. */
    @GetMapping("/fertility-episodes/{subjectCpid}/history")
    public ResponseEntity<?> fertilityEpisodeHistory(
            @PathVariable String subjectCpid,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String cpid = resolveSubject(subjectCpid, actorId);
        if (cpid == null) {
            return emptyList(requestId, correlationId);
        }
        return forward(() -> pct.getFertilityEpisodeHistory(cpid), "fertility episode history",
                requestId, correlationId);
    }

    /** Delivery records for a mother. {@code motherCpid} may be {@code "me"}. */
    @GetMapping("/delivery-records/mother/{motherCpid}")
    public ResponseEntity<?> deliveryRecordsForMother(
            @PathVariable String motherCpid,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String cpid = resolveSubject(motherCpid, actorId);
        if (cpid == null) {
            return emptyList(requestId, correlationId);
        }
        return forward(() -> pct.getDeliveryRecordsForMother(cpid), "delivery records",
                requestId, correlationId);
    }

    /** Delivery record for a pregnancy episode. */
    @GetMapping("/delivery-records/pregnancy/{pregnancyEpisodeId}")
    public ResponseEntity<?> deliveryRecordForPregnancy(
            @PathVariable UUID pregnancyEpisodeId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> pct.getDeliveryRecordForPregnancy(pregnancyEpisodeId),
                "delivery record for pregnancy", requestId, correlationId);
    }

    /**
     * Resolve the path subject to a real CPID without ever asking the browser to hold one.
     *
     * <p>A caller that already knows the real CPID (a clinician reading a patient's record) passes
     * it directly and this is a no-op. A citizen calling for herself sends the sentinel {@code "me"}
     * and it is resolved from her authenticated actor identity ({@code X-Actor-ID == healthId}), as
     * {@link ConfidentialMaternityController#resolveSubject} does. Resolution failure (no actor
     * header, or tshepo-identity unreachable) returns {@code null}.
     */
    private String resolveSubject(String requested, String actorId) {
        if (requested != null && !requested.isBlank() && !"me".equalsIgnoreCase(requested)) {
            return requested;
        }
        return actorId == null ? null : subjectResolution.cpidForHealthId(actorId);
    }

    /**
     * An unresolved subject on a list-shaped read. Every one of these five endpoints answers a
     * list, so "could not resolve who this is" is answered the same way pct answers "nothing you
     * may see": an empty list, never a 404 that would distinguish the two.
     */
    private static ResponseEntity<?> emptyList(String requestId, String correlationId) {
        Map<String, Object> body = new HashMap<>();
        body.put("data", List.of());
        body.put("meta", meta(requestId, correlationId));
        return ResponseEntity.ok(body);
    }

    /** Unresolved subject on an object-shaped read — null body inside 200, never 404. */
    private static ResponseEntity<?> emptyObject(String requestId, String correlationId) {
        Map<String, Object> body = new HashMap<>();
        body.put("data", null);
        body.put("meta", meta(requestId, correlationId));
        return ResponseEntity.ok(body);
    }

    private interface Call {
        ResponseEntity<JsonNode> invoke();
    }

    /**
     * Forward pct's answer with its status intact.
     *
     * <p>A domain rejection keeps its status and body; only transport failure becomes a 502.
     */
    private ResponseEntity<?> forward(Call call, String action, String requestId,
                                      String correlationId) {
        try {
            ResponseEntity<JsonNode> upstream = call.invoke();
            Map<String, Object> out = new HashMap<>();
            JsonNode body = upstream.getBody();
            // pct already answers {data, meta}; unwrap so this layer does not nest a second envelope.
            out.put("data", body != null && body.has("data") ? body.get("data") : body);
            out.put("meta", meta(requestId, correlationId));
            return ResponseEntity.status(upstream.getStatusCode()).body(out);
        } catch (HttpStatusCodeException upstream) {
            return passthrough(upstream, requestId, correlationId);
        } catch (RestClientException transport) {
            log.warn("Confidential reproductive {} transport failure: {}", action,
                    transport.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of(
                            "code", "PCT_UNAVAILABLE",
                            "message", transport.getMessage() == null
                                    ? "upstream error" : transport.getMessage(),
                            "clinical_note", "The " + action + " could not be reached. Do not treat "
                                    + "this as an absence of records — the record may exist and be "
                                    + "unreadable right now, which is a different fact from there "
                                    + "being nothing to see."),
                    "meta", meta(requestId, correlationId)));
        }
    }

    /**
     * An upstream domain rejection, verbatim.
     *
     * <p>Status and body both, so a client sees exactly what pct decided rather than this layer's
     * own error shape.
     */
    private ResponseEntity<?> passthrough(HttpStatusCodeException upstream, String requestId,
                                          String correlationId) {
        log.warn("Confidential reproductive: upstream rejected status={}", upstream.getStatusCode());
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getResponseBodyAsString());
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        return meta;
    }
}
