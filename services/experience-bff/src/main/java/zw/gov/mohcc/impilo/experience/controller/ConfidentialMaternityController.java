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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TelemonitoringServiceClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The citizen maternity surface: booking a pregnancy, and an honest verdict on home blood pressure.
 *
 * <h2>Why {@code /internal/v1/confidential/maternity} and not {@code /internal/v1/maternity}</h2>
 * <p>tshepo-authz classifies confidentiality from the path THE CLIENT CALLS, which for a mobile or web
 * caller is this path — not the pct path behind it. A BFF route mounted off the confidential lane gets
 * no {@code confidentialCategories} minted for it, so once obligation propagation is enabled the BFF
 * forwards an obligation carrying no grant and pct's fail-closed guard withholds every stamped record
 * from every requester, including the woman whose record it is.
 * {@code scripts/guard/check-confidential-lane-routing.sh} makes that a build failure.
 *
 * <h2>An upstream 409 must arrive as a 409</h2>
 * <p>The neighbouring maternity proxies wrap every exception into a 502 {@code PCT_UNAVAILABLE}, which
 * is right for a read that failed and wrong here. A duplicate booking is a 409 carrying the existing
 * episode id and a reconciliation task; flattened to "the service is down" it becomes an offline
 * booking the citizen is invited to retry forever. Domain outcomes pass through verbatim; only genuine
 * transport failure becomes a 502.
 *
 * <h2>SMBP composes, it never stores</h2>
 * <p>Telemonitoring owns {@code tm_readings} and is the single SHR writer of monitoring-band
 * Observations. This surface reads the series from telemonitoring and hands it to CKP for the verdict.
 * A second reading store here would guarantee two answers to "what was her blood pressure last
 * Tuesday", and the disagreement would surface between her phone and her midwife's chart.
 */
@RestController
@RequestMapping("/internal/v1/confidential/maternity")
public class ConfidentialMaternityController {

    private static final Logger log = LoggerFactory.getLogger(ConfidentialMaternityController.class);

    /**
     * How many readings to pull for a verdict. Generous on purpose: the sufficiency threshold is 12
     * counted readings and the first of each session is discarded from the mean, so a window sized to
     * the threshold would hold a diligent woman at INSUFFICIENT_DATA indefinitely.
     */
    private static final int READING_WINDOW = 200;

    /** The metric codes a home blood-pressure reading arrives under. */
    private static final String SYSTOLIC = "BP_SYSTOLIC";
    private static final String DIASTOLIC = "BP_DIASTOLIC";

    private final PctServiceClient pct;
    private final ClinicalKnowledgePlatformClient ckp;
    private final TelemonitoringServiceClient telemonitoring;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public ConfidentialMaternityController(PctServiceClient pct,
                                           ClinicalKnowledgePlatformClient ckp,
                                           TelemonitoringServiceClient telemonitoring,
                                           com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.pct = pct;
        this.ckp = ckp;
        this.telemonitoring = telemonitoring;
        this.objectMapper = objectMapper;
    }

    // ── pregnancy episode ─────────────────────────────────────────────────────

    /**
     * Book a pregnancy.
     *
     * <p>Statuses are pct's, forwarded unchanged: 201 created, 200 replayed offline packet, 409 already
     * booked with the id to reconcile against, 422 undatable.
     */
    @PostMapping("/pregnancy-episodes")
    public ResponseEntity<?> openPregnancyEpisode(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> pct.openPregnancyEpisode(body), "pregnancy booking",
                requestId, correlationId);
    }

    /** Her current pregnancy, or an empty body. Never a 404: absence and withholding read alike. */
    @GetMapping("/pregnancy-episodes/{subjectCpid}/current")
    public ResponseEntity<?> currentPregnancyEpisode(
            @PathVariable String subjectCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> pct.getCurrentPregnancyEpisode(subjectCpid), "current pregnancy",
                requestId, correlationId);
    }

    /** Her obstetric history. */
    @GetMapping("/pregnancy-episodes/{subjectCpid}")
    public ResponseEntity<?> pregnancyEpisodes(
            @PathVariable String subjectCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> pct.getPregnancyEpisodes(subjectCpid), "pregnancy history",
                requestId, correlationId);
    }

    // ── home blood pressure ───────────────────────────────────────────────────

    /**
     * The verdict on a monitoring plan's home blood-pressure series.
     *
     * <p>Reads the readings from telemonitoring, pairs them into blood pressures, and asks CKP what
     * they mean. {@code dangerSignPresent} is a query parameter and deliberately a boxed
     * {@link Boolean}: absent means the woman was not asked, which the engine reads as UNKNOWN. Sending
     * false on her behalf would assert that she has no danger signs, and there is no more dangerous
     * default available on this pathway.
     */
    @GetMapping("/smbp/verdict")
    public ResponseEntity<?> smbpVerdict(
            @RequestParam String planId,
            @RequestParam(required = false) Boolean dangerSignPresent,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> readings;
        int rawCount;
        try {
            ResponseEntity<String> upstream =
                    telemonitoring.readingsByPlan(planId, 0, READING_WINDOW);
            JsonNode payload = upstream.getBody() == null
                    ? null : objectMapper.readTree(upstream.getBody());
            readings = pairReadings(payload);
            rawCount = payload == null || !payload.has("readings")
                    ? 0 : payload.get("readings").size();
        } catch (HttpStatusCodeException upstream) {
            return passthrough(upstream, requestId, correlationId);
        } catch (Exception transport) {
            // A series that could not be read is NOT an empty series. Reporting it as
            // INSUFFICIENT_DATA would be a clinical statement about her blood pressure derived from a
            // network failure, and "keep monitoring" is the wrong advice for "we cannot see anything".
            log.warn("SMBP verdict: readings unavailable for plan={}: {}", planId,
                    transport.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of(
                            "code", "READINGS_UNAVAILABLE",
                            "message", transport.getMessage() == null
                                    ? "readings could not be read" : transport.getMessage(),
                            "clinical_note", "The home blood-pressure readings could not be read. Do "
                                    + "not treat this as an absence of readings, and do not render it "
                                    + "as a controlled or insufficient-data verdict."),
                    "meta", meta(requestId, correlationId)));
        }

        Map<String, Object> assessRequest = new LinkedHashMap<>();
        assessRequest.put("readings", readings);
        // Put the key in only when there is an answer. A null value and an absent key are the same to
        // Jackson here, but stating it keeps the intent visible: never fabricate the denial.
        if (dangerSignPresent != null) {
            assessRequest.put("dangerSignPresent", dangerSignPresent);
        }

        JsonNode verdict;
        try {
            verdict = ckp.smbpAssess(assessRequest);
        } catch (HttpStatusCodeException upstream) {
            return passthrough(upstream, requestId, correlationId);
        } catch (RestClientException transport) {
            log.warn("SMBP verdict: CKP unavailable for plan={}: {}", planId, transport.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of(
                            "code", "CKP_UNAVAILABLE",
                            "message", "the series verdict could not be computed",
                            "clinical_note", "The readings were retrieved but could not be assessed. "
                                    + "Do not render this as a controlled verdict."),
                    "meta", meta(requestId, correlationId)));
        }

        Map<String, Object> out = new HashMap<>();
        out.put("data", verdict);
        Map<String, Object> meta = meta(requestId, correlationId);
        meta.put("plan_id", planId);
        // Both counts, because they differ and the difference is diagnostic: a plan carrying 40 raw
        // rows that pairs into 3 blood pressures has a device or metric-code problem, and without the
        // raw count that surfaces only as an unexplained INSUFFICIENT_DATA.
        meta.put("raw_reading_count", rawCount);
        meta.put("paired_reading_count", readings.size());
        out.put("meta", meta);
        return ResponseEntity.ok(out);
    }

    /**
     * Pair telemonitoring's one-metric-per-row readings into blood pressures.
     *
     * <p>Telemonitoring stores a systolic and a diastolic as two rows sharing a {@code measuredAt}; a
     * blood pressure is only a blood pressure when both halves are present. An unpaired row is dropped
     * rather than completed from a neighbour: inventing a diastolic to go with a systolic would put a
     * number the woman never measured into the mean her care is decided on.
     *
     * <p>Quarantined and non-accepted readings are excluded. A reading telemonitoring has flagged as
     * unusable must not be laundered into a clinical mean by a different service reading the same table.
     *
     * <p>{@code firstOfSession} is not set, because telemonitoring records no session boundary. That
     * leaves every reading counting toward the mean, which is the safe direction: a reading wrongly
     * counted dilutes the mean slightly, whereas a reading wrongly discarded is one fewer toward
     * sufficiency and could hold a woman at INSUFFICIENT_DATA. The severe check sees every reading
     * either way, so no severe reading can be lost to this.
     */
    private static List<Map<String, Object>> pairReadings(JsonNode payload) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (payload == null || !payload.has("readings") || !payload.get("readings").isArray()) {
            return out;
        }
        Map<String, Integer> systolics = new LinkedHashMap<>();
        Map<String, Integer> diastolics = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();

        for (JsonNode r : payload.get("readings")) {
            String status = r.path("status").asText(null);
            if (status != null && !"ACCEPTED".equalsIgnoreCase(status)) {
                continue;
            }
            String at = r.path("measuredAt").asText(null);
            String code = r.path("metricCode").asText(null);
            if (at == null || code == null || r.path("metricValue").isNull()) {
                continue;
            }
            int value = (int) Math.round(r.path("metricValue").asDouble());
            if (SYSTOLIC.equalsIgnoreCase(code)) {
                systolics.put(at, value);
            } else if (DIASTOLIC.equalsIgnoreCase(code)) {
                diastolics.put(at, value);
            } else {
                continue;
            }
            if (!order.contains(at)) {
                order.add(at);
            }
        }

        for (String at : order) {
            Integer sys = systolics.get(at);
            Integer dia = diastolics.get(at);
            if (sys == null || dia == null) {
                continue;
            }
            Map<String, Object> reading = new LinkedHashMap<>();
            reading.put("systolic", sys);
            reading.put("diastolic", dia);
            reading.put("measuredAt", at);
            out.add(reading);
        }
        return out;
    }

    // ── plumbing ──────────────────────────────────────────────────────────────

    private interface Call {
        ResponseEntity<JsonNode> invoke();
    }

    /**
     * Forward pct's answer with its status intact.
     *
     * <p>A domain rejection keeps its status and body; only transport failure becomes a 502. The
     * neighbouring maternity proxies collapse both into 502, which on this lane would turn a 409
     * carrying a reconciliation task into an outage the client retries forever.
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
            log.warn("Confidential maternity {} transport failure: {}", action,
                    transport.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of(
                            "code", "PCT_UNAVAILABLE",
                            "message", transport.getMessage() == null
                                    ? "upstream error" : transport.getMessage(),
                            "clinical_note", "The maternity record could not be reached. Do not treat "
                                    + "this as an absence of a pregnancy, and do not re-submit a "
                                    + "booking without first checking whether it landed."),
                    "meta", meta(requestId, correlationId)));
        }
    }

    /**
     * An upstream domain rejection, verbatim.
     *
     * <p>Status and body both. The 409 body is the only place the existing episode id and the
     * reconciliation task exist, so rewriting it into this layer's own error shape would leave the
     * client a status it cannot act on.
     */
    private ResponseEntity<?> passthrough(HttpStatusCodeException upstream, String requestId,
                                          String correlationId) {
        log.warn("Confidential maternity: upstream rejected status={}", upstream.getStatusCode());
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
