package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.experience.service.SubjectResolutionService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The confidential reproductive READ proxy (W13-B), and the ways a proxy on this lane quietly
 * turns "you may not see this" into "this does not exist".
 *
 * <p>This is the read-only sibling of {@link ConfidentialMaternityProxyTest}: same lane-routing
 * requirement, same {@code "me"} resolution, same 502-vs-domain-status split. What is specific to
 * this lane is that every one of its five reads answers a LIST — so the empty-body case here is an
 * empty list, not a null object, and the transport-failure clinical note must say so explicitly
 * rather than let a caller mistake "could not be reached" for "she uses no contraception".
 */
class ConfidentialReproductiveReadProxyTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String[] LANE_MARKERS =
            {"confidential", "safeguarding", "protected-disclosure"};

    @Test
    @DisplayName("the controller is mounted on a lane the PDP will classify")
    void mountedOnAConfidentialLane() {
        String path = ConfidentialReproductiveReadController.class
                .getAnnotation(RequestMapping.class).value()[0].toLowerCase();
        // The PDP classifies the path THE CLIENT calls, not the pct path behind it. Off this lane,
        // no confidentialCategories are minted for the BFF route and pct's fail-closed guard
        // withholds every stamped record from every requester once propagation is on.
        assertTrue(Arrays.stream(LANE_MARKERS).anyMatch(path::contains),
                "path '" + path + "' carries no lane marker");
        assertTrue(path.contains("/confidential/"), "path '" + path + "' must contain /confidential/");
    }

    // ── "me" resolution ─────────────────────────────────────────────────────

    @Test
    @DisplayName("the citizen 'me' sentinel resolves to her CPID for every read on this lane")
    void meSentinelResolvesFromActorIdentity() {
        StubPct pct = new StubPct();
        ResponseEntity<?> r = controller(pct).contraception("me", null, "HID-citizen-1", "r", "c");

        assertEquals(200, r.getStatusCode().value());
        // The CPID pct actually received must be the resolved one, never the sentinel.
        assertEquals("CPID-FROM-HID-citizen-1", pct.lastContraceptionCpid);
    }

    @Test
    @DisplayName("an unresolved 'me' (no actor header) is an empty list, never a 404 or an error")
    void unresolvedSubjectIsAnEmptyList() {
        StubPct pct = new StubPct();
        ResponseEntity<?> r = controller(pct).contraception("me", null, null, "r", "c");

        assertEquals(200, r.getStatusCode().value());
        assertEquals(List.of(), ((Map<?, ?>) r.getBody()).get("data"));
        // pct must never have been called with an unresolved subject.
        assertEquals(null, pct.lastContraceptionCpid);
    }

    // ── real CPID forwarded, per endpoint ────────────────────────────────────

    @Test
    @DisplayName("a real CPID is forwarded verbatim to contraception coverage, with asOf")
    void realCpidForwardedToContraception() {
        StubPct pct = new StubPct();
        ResponseEntity<?> r = controller(pct).contraception("CPID-9", "2026-01-15", null, "r", "c");

        assertEquals(200, r.getStatusCode().value());
        assertEquals("CPID-9", pct.lastContraceptionCpid);
        assertEquals("2026-01-15", pct.lastAsOf);
        Map<?, ?> body = (Map<?, ?>) r.getBody();
        JsonNode data = (JsonNode) body.get("data");
        assertEquals(1, data.size());
        assertEquals("IMPLANT", data.get(0).get("methodCode").asText());
    }

    @Test
    @DisplayName("a real CPID is forwarded verbatim to contraception history")
    void realCpidForwardedToContraceptionHistory() {
        StubPct pct = new StubPct();
        ResponseEntity<?> r = controller(pct).contraceptionHistory("CPID-9", null, "r", "c");

        assertEquals(200, r.getStatusCode().value());
        assertEquals("CPID-9", pct.lastHistoryCpid);
    }

    @Test
    @DisplayName("a real motherCpid is forwarded verbatim to losses")
    void realCpidForwardedToLosses() {
        StubPct pct = new StubPct();
        ResponseEntity<?> r = controller(pct).losses("CPID-mother-1", null, "r", "c");

        assertEquals(200, r.getStatusCode().value());
        assertEquals("CPID-mother-1", pct.lastLossesCpid);
    }

    @Test
    @DisplayName("a real CPID is forwarded verbatim to TOP authorisations")
    void realCpidForwardedToTopAuthorisations() {
        StubPct pct = new StubPct();
        ResponseEntity<?> r = controller(pct).topAuthorisations("CPID-9", null, "r", "c");

        assertEquals(200, r.getStatusCode().value());
        assertEquals("CPID-9", pct.lastTopAuthCpid);
    }

    @Test
    @DisplayName("a real CPID is forwarded verbatim to terminations")
    void realCpidForwardedToTerminations() {
        StubPct pct = new StubPct();
        ResponseEntity<?> r = controller(pct).terminations("CPID-9", null, "r", "c");

        assertEquals(200, r.getStatusCode().value());
        assertEquals("CPID-9", pct.lastTerminationsCpid);
    }

    // ── 502 transport honesty ────────────────────────────────────────────────

    @Test
    @DisplayName("AN UNREACHABLE PCT IS A 502 THAT REFUSES TO IMPLY ABSENCE OF RECORDS")
    void transportFailureIsA502WithHonestClinicalNote() {
        StubPct pct = new StubPct();
        pct.transportFailure = true;
        ResponseEntity<?> r = controller(pct).contraception("CPID-9", null, null, "req", "corr");

        assertEquals(502, r.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) ((Map<?, ?>) r.getBody()).get("error");
        assertEquals("PCT_UNAVAILABLE", error.get("code"));
        // The obvious wrong answer here is an empty list on failure, which reads exactly like "she
        // uses no contraception" / "no losses recorded" / "never sought a termination".
        assertTrue(String.valueOf(error.get("clinical_note")).contains("not treat"),
                String.valueOf(error));
        assertTrue(String.valueOf(error.get("clinical_note")).toLowerCase()
                        .contains("absence of records"),
                String.valueOf(error));
    }

    @Test
    @DisplayName("an upstream domain rejection (e.g. 403 mis-scoped tenant) keeps its own status, not 502")
    void domainRejectionKeepsItsStatus() {
        StubPct pct = new StubPct();
        pct.rejection = HttpStatus.FORBIDDEN;
        ResponseEntity<?> r = controller(pct).terminations("CPID-9", null, "req", "corr");

        assertEquals(403, r.getStatusCode().value());
        assertTrue(String.valueOf(r.getBody()).contains("TENANT_MISMATCH"));
    }

    // ── empty list / null data stays 200 ─────────────────────────────────────

    @Test
    @DisplayName("pct answering an empty list stays 200 with an empty list, not an error")
    void emptyUpstreamListStays200() {
        StubPct pct = new StubPct();
        pct.emptyLists = true;
        ResponseEntity<?> r = controller(pct).losses("CPID-9", null, "r", "c");

        assertEquals(200, r.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) r.getBody();
        JsonNode data = (JsonNode) body.get("data");
        assertTrue(data.isArray() && data.isEmpty(), "expected an empty array, got: " + data);
    }

    @Test
    @DisplayName("pct answering a null data body stays 200 with null data, never a 404")
    void nullUpstreamDataStays200() {
        StubPct pct = new StubPct();
        pct.nullData = true;
        ResponseEntity<?> r = controller(pct).contraceptionHistory("CPID-9", null, "r", "c");

        assertEquals(200, r.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) r.getBody();
        assertTrue(body.containsKey("data"));
        // Jackson represents a JSON null as a NullNode, distinct from a real Java null — both must
        // read as "absent/withheld", never as a 404.
        JsonNode data = (JsonNode) body.get("data");
        assertTrue(data == null || data.isNull(), "expected null data, got: " + data);
    }

    // ────────────────────────────────────────────────────────────────────────

    private static ConfidentialReproductiveReadController controller(StubPct pct) {
        SubjectResolutionService subjects = new SubjectResolutionService(null) {
            @Override
            public String cpidForHealthId(String healthId) {
                return "CPID-FROM-" + healthId;
            }
        };
        return new ConfidentialReproductiveReadController(pct, subjects);
    }

    private static HttpClientErrorException rejection(HttpStatus status, String body) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), new HttpHeaders(),
                body.getBytes(), null);
    }

    private static final class StubPct extends PctServiceClient {
        boolean transportFailure = false;
        boolean emptyLists = false;
        boolean nullData = false;
        HttpStatus rejection = null;

        String lastContraceptionCpid;
        String lastAsOf;
        String lastHistoryCpid;
        String lastLossesCpid;
        String lastTopAuthCpid;
        String lastTerminationsCpid;

        StubPct() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), mapper);
        }

        private void maybeFail() {
            if (transportFailure) {
                throw new ResourceAccessException("pct unreachable");
            }
            if (rejection != null) {
                throw rejection(rejection, """
                        {"data": {"code": "TENANT_MISMATCH",
                                  "message": "subject not in caller's tenant"}}""");
            }
        }

        private ResponseEntity<JsonNode> answer(ArrayNode single) {
            maybeFail();
            if (nullData) {
                return ResponseEntity.ok(mapper.createObjectNode().putNull("data"));
            }
            if (emptyLists) {
                return ResponseEntity.ok(mapper.createObjectNode().set("data", mapper.createArrayNode()));
            }
            return ResponseEntity.ok(mapper.createObjectNode().set("data", single));
        }

        @Override
        public ResponseEntity<JsonNode> getContraceptionCoverage(String subjectCpid, String asOf) {
            lastContraceptionCpid = null;
            maybeFail();
            lastContraceptionCpid = subjectCpid;
            lastAsOf = asOf;
            if (nullData) {
                return ResponseEntity.ok(mapper.createObjectNode().putNull("data"));
            }
            if (emptyLists) {
                return ResponseEntity.ok(mapper.createObjectNode().set("data", mapper.createArrayNode()));
            }
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("methodCode", "IMPLANT"));
            return ResponseEntity.ok(mapper.createObjectNode().set("data", arr));
        }

        @Override
        public ResponseEntity<JsonNode> getContraceptionHistory(String subjectCpid) {
            lastHistoryCpid = null;
            maybeFail();
            lastHistoryCpid = subjectCpid;
            return answer(mapper.createArrayNode());
        }

        @Override
        public ResponseEntity<JsonNode> getPregnancyLosses(String motherCpid) {
            lastLossesCpid = null;
            maybeFail();
            lastLossesCpid = motherCpid;
            return answer(mapper.createArrayNode());
        }

        @Override
        public ResponseEntity<JsonNode> getTopAuthorisations(String subjectCpid) {
            lastTopAuthCpid = null;
            maybeFail();
            lastTopAuthCpid = subjectCpid;
            return answer(mapper.createArrayNode());
        }

        @Override
        public ResponseEntity<JsonNode> getTerminations(String subjectCpid) {
            lastTerminationsCpid = null;
            maybeFail();
            lastTerminationsCpid = subjectCpid;
            return answer(mapper.createArrayNode());
        }
    }
}
