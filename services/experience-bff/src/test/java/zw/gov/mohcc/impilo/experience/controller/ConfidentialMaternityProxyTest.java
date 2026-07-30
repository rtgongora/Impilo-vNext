package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TelemonitoringServiceClient;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two owed citizen maternity surfaces, and the ways a proxy quietly ruins them.
 *
 * <p>Each test here corresponds to a specific way the surrounding code would otherwise behave. The
 * neighbouring maternity proxies wrap every exception into a 502, so the 409 test is not paranoia
 * about a hypothetical: it is the behaviour of the file next door, applied to a path where it loses an
 * offline booking. Likewise the readings-unavailable test exists because the obvious implementation —
 * an empty list on failure — produces a clinical verdict about a woman's blood pressure derived from a
 * network timeout.
 */
class ConfidentialMaternityProxyTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String[] LANE_MARKERS =
            {"confidential", "safeguarding", "protected-disclosure"};

    @Test
    @DisplayName("both controllers are mounted on a lane the PDP will classify")
    void mountedOnAConfidentialLane() {
        for (Class<?> c : new Class<?>[]{ConfidentialMaternityController.class,
                ConfidentialCommunityPostnatalController.class}) {
            String path = c.getAnnotation(RequestMapping.class).value()[0].toLowerCase();
            // The PDP classifies the path THE CLIENT calls, not the pct path behind it. Off this lane,
            // no confidentialCategories are minted for the BFF route and pct's fail-closed guard
            // withholds every stamped record from every requester once propagation is on.
            assertTrue(Arrays.stream(LANE_MARKERS).anyMatch(path::contains),
                    c.getSimpleName() + " path '" + path + "' carries no lane marker");
        }
    }

    // ── pregnancy booking ─────────────────────────────────────────────────────

    @Test
    @DisplayName("A DUPLICATE BOOKING ARRIVES AS 409 WITH ITS RECONCILIATION BODY, NOT AS A 502")
    void duplicateBookingKeepsItsStatusAndItsBody() {
        StubPct pct = new StubPct();
        pct.conflict = true;
        ResponseEntity<?> r = controller(pct, new StubCkp(), new StubTelemonitoring())
                .openPregnancyEpisode(Map.of("subjectCpid", "CPID-1"), "req-1", "corr-1");

        // The file next door turns every exception into 502 PCT_UNAVAILABLE. Here that would tell a
        // citizen the service is down and invite her to retry a booking that already exists.
        assertEquals(409, r.getStatusCode().value());
        String body = String.valueOf(r.getBody());
        // The existing id and the task live only in pct's body. Rewriting it into this layer's error
        // shape would leave the client a status it cannot act on.
        assertTrue(body.contains("existing_pregnancy_episode_id"), body);
        assertTrue(body.contains("reconciliation_task"), body);
    }

    @Test
    @DisplayName("a 422 undatable booking is forwarded as 422, not softened and not masked")
    void undatableBookingKeepsIts422() {
        StubPct pct = new StubPct();
        pct.unprocessable = true;
        ResponseEntity<?> r = controller(pct, new StubCkp(), new StubTelemonitoring())
                .openPregnancyEpisode(Map.of("subjectCpid", "CPID-1"), "req-2", "corr-2");

        // Retrying an identical packet will never help, and only the status says so.
        assertEquals(422, r.getStatusCode().value());
        assertTrue(String.valueOf(r.getBody()).contains("PREGNANCY_UNDATABLE"));
    }

    @Test
    @DisplayName("a new booking forwards 201 and a replayed one 200 — the client must be able to tell")
    void createdAndReplayedAreDistinguishable() {
        StubPct created = new StubPct();
        assertEquals(201, controller(created, new StubCkp(), new StubTelemonitoring())
                .openPregnancyEpisode(Map.of("subjectCpid", "CPID-1"), "r", "c")
                .getStatusCode().value());

        StubPct replayed = new StubPct();
        replayed.bookingStatus = HttpStatus.OK;
        // Collapsed to one status, an offline client either double-books or treats its own successful
        // sync as a failure.
        assertEquals(200, controller(replayed, new StubCkp(), new StubTelemonitoring())
                .openPregnancyEpisode(Map.of("subjectCpid", "CPID-1"), "r", "c")
                .getStatusCode().value());
    }

    @Test
    @DisplayName("an unreachable pct is 502 telling the client NOT to re-submit blindly")
    void transportFailureIsA502WithHonestAdvice() {
        StubPct pct = new StubPct();
        pct.transportFailure = true;
        ResponseEntity<?> r = controller(pct, new StubCkp(), new StubTelemonitoring())
                .openPregnancyEpisode(Map.of("subjectCpid", "CPID-1"), "req-3", "corr-3");

        assertEquals(502, r.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) ((Map<?, ?>) r.getBody()).get("error");
        assertEquals("PCT_UNAVAILABLE", error.get("code"));
        // A booking may have landed. "Retry" and "check first" are different instructions.
        assertTrue(String.valueOf(error.get("clinical_note")).contains("whether it landed"));
    }

    @Test
    @DisplayName("an absent current pregnancy stays a 200 with an empty body, never a 404")
    void absentCurrentPregnancyIsNot404() {
        StubPct pct = new StubPct();
        pct.currentPregnancyNull = true;
        ResponseEntity<?> r = controller(pct, new StubCkp(), new StubTelemonitoring())
                .currentPregnancyEpisode("CPID-1", "r", "c");

        // "She is not pregnant" and "she is pregnant and you may not know" are the two cases this lane
        // exists to make indistinguishable.
        assertEquals(200, r.getStatusCode().value());
    }

    // ── SMBP verdict ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("readings are paired into blood pressures and the verdict comes from CKP")
    void readingsArePairedAndAssessed() {
        StubTelemonitoring tm = new StubTelemonitoring();
        StubCkp ckp = new StubCkp();
        ResponseEntity<?> r = controller(new StubPct(), ckp, tm)
                .smbpVerdict("plan-1", false, "req-4", "corr-4");

        assertEquals(200, r.getStatusCode().value());
        Map<?, ?> meta = (Map<?, ?>) ((Map<?, ?>) r.getBody()).get("meta");
        // Two usable blood pressures out of seven rows: one unpaired systolic and one quarantine-broken
        // pair are dropped.
        assertEquals(2, meta.get("paired_reading_count"));
        // Both counts, because the difference is diagnostic: without the raw count a device or
        // metric-code problem surfaces only as an unexplained INSUFFICIENT_DATA.
        assertEquals(7, meta.get("raw_reading_count"));
        assertEquals(2, ckp.lastReadingCount);
    }

    @Test
    @DisplayName("AN UNPAIRED SYSTOLIC IS DROPPED, never completed from a neighbouring reading")
    void anUnpairedReadingIsNotInvented() {
        StubCkp ckp = new StubCkp();
        controller(new StubPct(), ckp, new StubTelemonitoring())
                .smbpVerdict("plan-1", false, "r", "c");

        // Inventing a diastolic to go with a systolic would put a number the woman never measured into
        // the mean her care is decided on.
        assertFalse(ckp.lastReadings.toString().contains("\"systolic\":141"),
                "the unpaired 141 systolic must not reach the engine: " + ckp.lastReadings);
    }

    @Test
    @DisplayName("a quarantined reading never reaches the clinical mean")
    void quarantinedReadingsAreExcluded() {
        StubCkp ckp = new StubCkp();
        controller(new StubPct(), ckp, new StubTelemonitoring())
                .smbpVerdict("plan-1", false, "r", "c");

        // A reading telemonitoring has flagged unusable must not be laundered into a mean by a
        // different service reading the same table.
        assertFalse(ckp.lastReadings.toString().contains("\"systolic\":200"),
                "the quarantined 200 must not reach the engine: " + ckp.lastReadings);
        // And its ACCEPTED diastolic goes with it. Keeping the surviving half and pairing it with a
        // neighbouring systolic would build a blood pressure out of two different measurements.
        assertFalse(ckp.lastReadings.toString().contains("\"diastolic\":88"),
                "the orphaned 88 diastolic must not be re-paired: " + ckp.lastReadings);
    }

    @Test
    @DisplayName("an unasked danger sign is not sent as a denial")
    void nullDangerSignIsOmittedNotFalsified() {
        StubCkp ckp = new StubCkp();
        controller(new StubPct(), ckp, new StubTelemonitoring())
                .smbpVerdict("plan-1", null, "r", "c");

        // Sending false on her behalf asserts she has no danger signs, which is the most dangerous
        // default available on this pathway.
        assertFalse(ckp.lastRequest.containsKey("dangerSignPresent"),
                "an absent answer must stay absent: " + ckp.lastRequest);

        StubCkp explicit = new StubCkp();
        controller(new StubPct(), explicit, new StubTelemonitoring())
                .smbpVerdict("plan-1", false, "r", "c");
        assertEquals(false, explicit.lastRequest.get("dangerSignPresent"));
    }

    @Test
    @DisplayName("UNREADABLE READINGS ARE A 502, NEVER AN EMPTY SERIES")
    void unavailableReadingsAreNotAnEmptySeries() {
        StubTelemonitoring tm = new StubTelemonitoring();
        tm.transportFailure = true;
        StubCkp ckp = new StubCkp();
        ResponseEntity<?> r = controller(new StubPct(), ckp, tm)
                .smbpVerdict("plan-1", false, "req-5", "corr-5");

        assertEquals(502, r.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) ((Map<?, ?>) r.getBody()).get("error");
        assertEquals("READINGS_UNAVAILABLE", error.get("code"));
        // The obvious implementation — an empty list on failure — yields INSUFFICIENT_DATA, which is a
        // clinical statement about her blood pressure derived from a network timeout, and "keep
        // monitoring" is the wrong advice for "we cannot see anything".
        assertTrue(String.valueOf(error.get("clinical_note")).contains("absence of readings"));
        assertEquals(0, ckp.calls, "the engine must not be asked about a series nobody could read");
    }

    @Test
    @DisplayName("an unreachable CKP is a 502 that forbids rendering a controlled verdict")
    void unreachableCkpIsNotAVerdict() {
        StubCkp ckp = new StubCkp();
        ckp.transportFailure = true;
        ResponseEntity<?> r = controller(new StubPct(), ckp, new StubTelemonitoring())
                .smbpVerdict("plan-1", false, "req-6", "corr-6");

        assertEquals(502, r.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) ((Map<?, ?>) r.getBody()).get("error");
        assertEquals("CKP_UNAVAILABLE", error.get("code"));
        assertTrue(String.valueOf(error.get("clinical_note")).contains("controlled verdict"));
    }

    // ── CHW postnatal ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a replayed CHW packet forwards 200 and the existing contact")
    void replayedContactForwards200() {
        StubPct pct = new StubPct();
        pct.contactStatus = HttpStatus.OK;
        ResponseEntity<?> r = new ConfidentialCommunityPostnatalController(pct)
                .recordContact(Map.of("motherCpid", "CPID-1", "contactSetting", "HOME"), "r", "c");

        // A surface that collapsed 200 and 201 would leave a CHW who synced twice with a woman recorded
        // as having had two day-3 visits, which then reads as coverage nobody delivered.
        assertEquals(200, r.getStatusCode().value());
    }

    @Test
    @DisplayName("pct's 422 refusal reaches the CHW with its reason intact")
    void refusalKeepsItsReason() {
        StubPct pct = new StubPct();
        pct.unprocessable = true;
        ResponseEntity<?> r = new ConfidentialCommunityPostnatalController(pct)
                .recordContact(Map.of("motherCpid", "CPID-1"), "r", "c");

        assertEquals(422, r.getStatusCode().value());
        // "Invalid request" is not something a CHW can fix in the field; the reason is.
        assertTrue(String.valueOf(r.getBody()).contains("PNC_SETTING_REQUIRED"));
    }

    @Test
    @DisplayName("an unreachable pct tells the CHW to re-send the same packet, not to re-enter the visit")
    void submitFailureAdvisesReplayNotReentry() {
        StubPct pct = new StubPct();
        pct.transportFailure = true;
        ResponseEntity<?> r = new ConfidentialCommunityPostnatalController(pct)
                .recordContact(Map.of("motherCpid", "CPID-1", "contactSetting", "HOME"), "r", "c");

        assertEquals(502, r.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) ((Map<?, ?>) r.getBody()).get("error");
        // Re-sending is safe because clientOfflineId makes the replay idempotent. Re-entering creates
        // the duplicate the offline id exists to prevent.
        assertTrue(String.valueOf(error.get("clinical_note")).contains("same packet"));
        assertTrue(String.valueOf(error.get("clinical_note")).contains("Do not re-enter"));
    }

    @Test
    @DisplayName("the CHW submit body is forwarded verbatim, nulls and all")
    void theBodyIsNotNormalisedOnTheWayThrough() {
        StubPct pct = new StubPct();
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("motherCpid", "CPID-1");
        body.put("contactSetting", "HOME");
        body.put("screeningComplete", null);
        body.put("dangerSignsPresent", null);
        body.put("breastfeedingStatus", "NOT_ASSESSED");

        new ConfidentialCommunityPostnatalController(pct).recordContact(body, "r", "c");

        // V436 chk_pnc_screening_gate refuses a danger-sign finding without a completed screen. Filling
        // either in here would manufacture the reassuring all-clear one layer above the schema.
        assertNotNull(pct.lastContactBody);
        assertTrue(pct.lastContactBody.containsKey("dangerSignsPresent"));
        assertEquals(null, pct.lastContactBody.get("dangerSignsPresent"));
        assertEquals("NOT_ASSESSED", pct.lastContactBody.get("breastfeedingStatus"));
    }

    // ────────────────────────────────────────────────────────────────────────

    private static ConfidentialMaternityController controller(StubPct pct, StubCkp ckp,
                                                             StubTelemonitoring tm) {
        return new ConfidentialMaternityController(pct, ckp, tm, mapper);
    }

    private static HttpClientErrorException rejection(HttpStatus status, String body) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), new HttpHeaders(),
                body.getBytes(), null);
    }

    private static final class StubPct extends PctServiceClient {
        boolean conflict = false;
        boolean unprocessable = false;
        boolean transportFailure = false;
        boolean currentPregnancyNull = false;
        HttpStatus bookingStatus = HttpStatus.CREATED;
        HttpStatus contactStatus = HttpStatus.CREATED;
        Map<String, Object> lastContactBody;

        StubPct() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), mapper);
        }

        private void maybeFail(String conflictBody) {
            if (transportFailure) {
                throw new ResourceAccessException("pct unreachable");
            }
            if (conflict) {
                throw rejection(HttpStatus.CONFLICT, conflictBody);
            }
            if (unprocessable) {
                throw rejection(HttpStatus.UNPROCESSABLE_ENTITY, """
                        {"data": {"code": "PREGNANCY_UNDATABLE",
                                  "message": "no usable dating basis"}}""");
            }
        }

        @Override
        public ResponseEntity<JsonNode> openPregnancyEpisode(Map<String, Object> body) {
            maybeFail("""
                    {"data": {"code": "PREGNANCY_ALREADY_BOOKED",
                              "existing_pregnancy_episode_id": "11111111-1111-4111-8111-111111111111",
                              "reconciliation_task": "Open the existing episode and reconcile."}}""");
            ObjectNode data = mapper.createObjectNode()
                    .put("pregnancy_episode_id", "pe-1")
                    .put("replayed", bookingStatus == HttpStatus.OK);
            return ResponseEntity.status(bookingStatus)
                    .body(mapper.createObjectNode().set("data", data));
        }

        @Override
        public ResponseEntity<JsonNode> getCurrentPregnancyEpisode(String subjectCpid) {
            if (currentPregnancyNull) {
                return ResponseEntity.ok(mapper.createObjectNode().putNull("data"));
            }
            return ResponseEntity.ok(mapper.createObjectNode()
                    .set("data", mapper.createObjectNode().put("pregnancy_episode_id", "pe-1")));
        }

        @Override
        public ResponseEntity<JsonNode> recordPostnatalContact(Map<String, Object> body) {
            lastContactBody = body;
            if (transportFailure) {
                throw new ResourceAccessException("pct unreachable");
            }
            if (unprocessable) {
                throw rejection(HttpStatus.UNPROCESSABLE_ENTITY, """
                        {"data": {"code": "PNC_SETTING_REQUIRED",
                                  "message": "a postnatal contact must say where it happened"}}""");
            }
            return ResponseEntity.status(contactStatus).body(mapper.createObjectNode()
                    .set("data", mapper.createObjectNode()
                            .put("postnatal_contact_id", "pnc-1")
                            .put("replayed", contactStatus == HttpStatus.OK)));
        }
    }

    private static final class StubCkp extends ClinicalKnowledgePlatformClient {
        boolean transportFailure = false;
        int calls = 0;
        Map<String, Object> lastRequest = Map.of();
        Object lastReadings = java.util.List.of();
        int lastReadingCount = -1;

        StubCkp() {
            super(new RestTemplate(), new ClinicalPlatformProperties("http://ckp.test"));
        }

        @Override
        @SuppressWarnings("unchecked")
        public JsonNode smbpAssess(Map<String, Object> body) {
            calls++;
            if (transportFailure) {
                throw new ResourceAccessException("ckp unreachable");
            }
            lastRequest = body;
            lastReadings = body.get("readings");
            lastReadingCount = ((java.util.List<Object>) body.get("readings")).size();
            return mapper.createObjectNode().put("verdict", "INSUFFICIENT_DATA");
        }
    }

    private static final class StubTelemonitoring extends TelemonitoringServiceClient {
        boolean transportFailure = false;

        StubTelemonitoring() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public ResponseEntity<String> readingsByPlan(String planId, int page, int size) {
            if (transportFailure) {
                throw new ResourceAccessException("telemonitoring unreachable");
            }
            // Six rows resolving to two blood pressures:
            //   03-01  complete pair                                        -> kept
            //   03-02  systolic with no diastolic                           -> dropped, unpaired
            //   03-03  QUARANTINED systolic with an ACCEPTED diastolic      -> dropped, whole reading
            //   03-04  complete pair                                        -> kept
            // The 03-03 case is the interesting one: excluding the quarantined half leaves an orphan
            // diastolic, and the pairing must discard that too rather than keep half a reading.
            ArrayNode readings = mapper.createArrayNode();
            readings.add(reading("2026-03-01T08:00:00Z", "BP_SYSTOLIC", 128, "ACCEPTED"));
            readings.add(reading("2026-03-01T08:00:00Z", "BP_DIASTOLIC", 82, "ACCEPTED"));
            readings.add(reading("2026-03-02T08:00:00Z", "BP_SYSTOLIC", 141, "ACCEPTED"));
            readings.add(reading("2026-03-03T08:00:00Z", "BP_SYSTOLIC", 200, "QUARANTINED"));
            readings.add(reading("2026-03-03T08:00:00Z", "BP_DIASTOLIC", 88, "ACCEPTED"));
            readings.add(reading("2026-03-04T08:00:00Z", "BP_SYSTOLIC", 132, "ACCEPTED"));
            readings.add(reading("2026-03-04T08:00:00Z", "BP_DIASTOLIC", 85, "ACCEPTED"));
            ObjectNode body = mapper.createObjectNode();
            body.set("readings", readings);
            body.put("page", 0).put("totalElements", readings.size()).put("totalPages", 1);
            return ResponseEntity.ok(body.toString());
        }

        private static ObjectNode reading(String at, String code, int value, String status) {
            ObjectNode r = mapper.createObjectNode();
            r.put("measuredAt", at);
            r.put("metricCode", code);
            r.put("metricValue", value);
            r.put("status", status);
            return r;
        }
    }
}
