package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.HashMap;
import java.util.Map;

/**
 * The CHW community postnatal surface — the day-3 home visit, captured offline and synced later.
 *
 * <h2>Where it belongs</h2>
 * <p>The provider app's existing Outreach mode, not a new workspace. A postnatal contact that happens
 * at home, by a CHW, for a woman who will not return to a facility is the contact the WHO PNC schedule
 * is worst at capturing — and until pct {@code V436} there was nowhere to record it at all.
 *
 * <h2>Why the confidential lane</h2>
 * <p>A contact carrying contraception content is stamped {@code SEXUAL_REPRODUCTIVE_HEALTH}, and the
 * PDP classifies from the path the CLIENT calls. Mounted anywhere else, this route would be handed no
 * confidential category, and after the governance flip pct's fail-closed guard would withhold the
 * contact from the CHW who recorded it. Note the asymmetry the stamper implements: a routine PNC visit
 * is deliberately unstamped, so a client must assume neither that every postnatal record is protected
 * nor that none is.
 *
 * <h2>Offline is first-class, and a replay is a success</h2>
 * <p>{@code clientOfflineId} is unique per tenant, so a replayed packet returns the existing contact
 * with 200 rather than minting a duplicate visit; a new one is 201. Both pass through from pct
 * unchanged. A surface that collapsed them would leave a CHW who synced twice with a woman recorded as
 * having had two day-3 visits, which then reads as coverage nobody delivered.
 */
@RestController
@RequestMapping("/internal/v1/confidential/community/postnatal")
public class ConfidentialCommunityPostnatalController {

    private static final Logger log =
            LoggerFactory.getLogger(ConfidentialCommunityPostnatalController.class);

    private final PctServiceClient pct;

    public ConfidentialCommunityPostnatalController(PctServiceClient pct) {
        this.pct = pct;
    }

    /**
     * Record a postnatal contact.
     *
     * <p>The body is forwarded as built. In particular {@code screeningComplete},
     * {@code dangerSignsPresent} and {@code breastfeedingStatus} are passed through with their nulls:
     * an unscreened contact must arrive at pct as unscreened, where {@code V436 chk_pnc_screening_gate}
     * refuses a danger-sign finding without a completed screen. Filling either in here would
     * manufacture the reassuring all-clear the schema exists to refuse, one layer above the schema.
     *
     * <p>{@code contactSetting} is likewise not defaulted. HOME, COMMUNITY and VIRTUAL are first-class
     * settings; a default of FACILITY would relabel every CHW visit as a clinic attendance. pct refuses
     * a contact with no setting as 422, and that refusal is forwarded rather than papered over.
     */
    @PostMapping("/contacts")
    public ResponseEntity<?> recordContact(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> pct.recordPostnatalContact(body), "postnatal contact submit",
                requestId, correlationId);
    }

    /**
     * A mother's postnatal contacts.
     *
     * <p>An empty list means "nothing you may see", and the caller cannot tell that from "nothing
     * exists" — deliberately, because a response distinguishable from absence tells the reader that the
     * confidential contact is there, which is most of what confidentiality was protecting.
     */
    @GetMapping("/contacts/{motherCpid}")
    public ResponseEntity<?> contacts(
            @PathVariable String motherCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> pct.getPostnatalContacts(motherCpid), "postnatal contact read",
                requestId, correlationId);
    }

    // ── plumbing ──────────────────────────────────────────────────────────────

    private interface Call {
        ResponseEntity<JsonNode> invoke();
    }

    /**
     * Forward pct's answer with its status intact; only transport failure becomes a 502.
     *
     * <p>The 502 message matters more here than on a read surface. A CHW told "the service is down"
     * after a submit does not know whether her visit landed, and the honest instruction is to re-sync
     * the same packet — which is safe precisely because {@code clientOfflineId} makes the replay
     * idempotent. Telling her to re-enter the visit would create the duplicate the offline id prevents.
     */
    private ResponseEntity<?> forward(Call call, String action, String requestId,
                                      String correlationId) {
        try {
            ResponseEntity<JsonNode> upstream = call.invoke();
            JsonNode body = upstream.getBody();
            Map<String, Object> out = new HashMap<>();
            out.put("data", body != null && body.has("data") ? body.get("data") : body);
            out.put("meta", meta(requestId, correlationId));
            return ResponseEntity.status(upstream.getStatusCode()).body(out);
        } catch (HttpStatusCodeException upstream) {
            // pct's 422 refusals (no setting, a referral with no reason) carry the reason. Rewritten
            // into this layer's own shape they would reach the CHW as "invalid request", which is not
            // something she can fix in the field.
            log.warn("Community postnatal {} rejected upstream status={}", action,
                    upstream.getStatusCode());
            return ResponseEntity.status(upstream.getStatusCode())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, requestId)
                    .header(CompanionHeaders.CORRELATION_ID, correlationId)
                    .body(upstream.getResponseBodyAsString());
        } catch (RestClientException transport) {
            log.warn("Community postnatal {} transport failure: {}", action, transport.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of(
                            "code", "PCT_UNAVAILABLE",
                            "message", transport.getMessage() == null
                                    ? "upstream error" : transport.getMessage(),
                            "clinical_note", "The postnatal record could not be reached. Re-send the "
                                    + "same packet with its original clientOfflineId — the replay is "
                                    + "idempotent. Do not re-enter the visit, and do not treat this as "
                                    + "an absence of postnatal contacts."),
                    "meta", meta(requestId, correlationId)));
        }
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        return meta;
    }
}
