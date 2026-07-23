package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TelemonitoringServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OF-B23/B27/B28 — telemonitoring proxy contract: verbatim payload passthrough,
 * coded-envelope preservation on domain rejections (§14.6 closure refusals must
 * reach the shell with their real code), transport failure → coded 502,
 * idempotency-key passthrough on writes, and the citizen self lane resolving
 * the patient from X-Actor-ID (never a caller-supplied CPID).
 */
class TelemonitoringBffControllerTest {

    private static final String EPISODES_PAYLOAD =
            "[{\"episodeId\":\"E-1\",\"severity\":\"RED\",\"status\":\"OPEN\","
                    + "\"contributingReadings\":[{\"metricCode\":\"SBP\",\"value\":184}],"
                    + "\"responseDeadlineAt\":\"2026-07-23T10:00:00Z\"}]";

    private static final String RESOLVE_PAYLOAD =
            "{\"episodeId\":\"E-1\",\"status\":\"RESOLVED\",\"resolvedBy\":\"nurse-1\","
                    + "\"assessment\":\"BP settled\",\"actionTaken\":\"Repeat confirmed normal\",\"outcome\":\"RESOLVED_NO_HARM\"}";

    private static final String PLANS_PAYLOAD =
            "[{\"id\":\"P-1\",\"patientCpid\":\"CPID-9\",\"programmeCode\":\"HTN\",\"status\":\"ACTIVE\"}]";

    @Test
    void listAlertEpisodesForwardsPayloadWithMetaHeaders() {
        StubClient client = new StubClient();
        TelemonitoringBffController controller = new TelemonitoringBffController(client);

        ResponseEntity<String> response =
                controller.listAlertEpisodes(null, "OPEN", null, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("corr-1", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals(EPISODES_PAYLOAD, response.getBody());
        assertEquals("OPEN", client.lastEpisodeStatus);
    }

    @Test
    void resolveForwardsBodyAndIdempotencyKeyVerbatim() {
        StubClient client = new StubClient();
        TelemonitoringBffController controller = new TelemonitoringBffController(client);
        String closure = "{\"assessment\":\"BP settled\",\"action\":\"Repeat confirmed normal\","
                + "\"outcome\":\"RESOLVED_NO_HARM\",\"resolvedBy\":\"nurse-1\"}";

        ResponseEntity<String> response =
                controller.resolveEpisode("E-1", closure, "idem-key-1", "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(RESOLVE_PAYLOAD, response.getBody());
        assertEquals(closure, client.lastResolveBody);
        assertEquals("idem-key-1", client.lastIdempotencyKey);
    }

    @Test
    void codedDomainRejectionPassesThroughWithRealStatusAndEnvelope() {
        String envelope = "{\"error\":{\"code\":\"TM_ALERT_INVALID_TRANSITION\","
                + "\"message\":\"RESOLVED is not reachable from OPEN\"},\"timestamp\":\"2026-07-23T09:00:00Z\"}";
        TelemonitoringBffController controller =
                new TelemonitoringBffController(new FailingClient(
                        HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict",
                                jsonHeaders(), envelope.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

        ResponseEntity<String> response =
                controller.resolveEpisode("E-1", "{}", "idem-key-2", "req-3", "corr-3");

        assertEquals(409, response.getStatusCode().value());
        assertEquals(envelope, response.getBody());
        assertEquals("corr-3", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
    }

    @Test
    void transportFailureBecomesCoded502NotAMaskedOutcome() {
        TelemonitoringBffController controller =
                new TelemonitoringBffController(new FailingClient(
                        new ResourceAccessException("Connection refused")));

        ResponseEntity<String> response =
                controller.listAlertEpisodes(null, null, null, "req-4", "corr-4");

        assertEquals(502, response.getStatusCode().value());
        assertTrue(response.getBody().contains("TELEMONITORING_UNAVAILABLE"));
        assertEquals("req-4", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
    }

    @Test
    void myPlansLaneResolvesPatientFromActorHeader() {
        StubClient client = new StubClient();
        TelemonitoringBffController controller = new TelemonitoringBffController(client);

        ResponseEntity<String> response = controller.listMyPlans("CPID-9", "req-5", "corr-5");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(PLANS_PAYLOAD, response.getBody());
        assertEquals("CPID-9", client.lastPlanPatientCpid);
    }

    @Test
    void readingsByPlanForwardsPagingParameters() {
        StubClient client = new StubClient();
        TelemonitoringBffController controller = new TelemonitoringBffController(client);

        ResponseEntity<String> response =
                controller.readingsByPlan("P-1", 2, 25, "req-6", "corr-6");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"readings\""));
        assertEquals(2, client.lastReadingsPage);
        assertEquals(25, client.lastReadingsSize);
    }

    @Test
    void ladderStepForwardsRungPayload() {
        StubClient client = new StubClient();
        TelemonitoringBffController controller = new TelemonitoringBffController(client);

        ResponseEntity<String> response = controller.recordLadderStep(
                "E-1", "{\"rung\":4,\"note\":\"CHW visit requested\"}", null, "req-7", "corr-7");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"rung\":4,\"note\":\"CHW visit requested\"}", client.lastLadderBody);
    }

    @Test
    void devicePostureResolveForwardsHonestPosture() {
        StubClient client = new StubClient();
        TelemonitoringBffController controller = new TelemonitoringBffController(client);

        ResponseEntity<String> response =
                controller.resolveDeviceAssignment("DEV-1", "req-8", "corr-8");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"calibrationPosture\":\"DEGRADED\""));
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static class StubClient extends TelemonitoringServiceClient {
        String lastEpisodeStatus;
        String lastResolveBody;
        String lastLadderBody;
        String lastIdempotencyKey;
        String lastPlanPatientCpid;
        int lastReadingsPage;
        int lastReadingsSize;

        StubClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> listPlansByPatient(String patientCpid) {
            this.lastPlanPatientCpid = patientCpid;
            return json(200, PLANS_PAYLOAD);
        }

        @Override
        public ResponseEntity<String> listAlertEpisodes(String planId, String status, String deviceRef) {
            this.lastEpisodeStatus = status;
            return json(200, EPISODES_PAYLOAD);
        }

        @Override
        public ResponseEntity<String> resolveEpisode(String episodeId, String body, String idempotencyKey) {
            this.lastResolveBody = body;
            this.lastIdempotencyKey = idempotencyKey;
            return json(200, RESOLVE_PAYLOAD);
        }

        @Override
        public ResponseEntity<String> recordLadderStep(String episodeId, String body, String idempotencyKey) {
            this.lastLadderBody = body;
            return json(200, "{\"episodeId\":\"E-1\",\"currentRung\":4,\"status\":\"ESCALATED\"}");
        }

        @Override
        public ResponseEntity<String> readingsByPlan(String planId, int page, int size) {
            this.lastReadingsPage = page;
            this.lastReadingsSize = size;
            return json(200, "{\"readings\":[],\"page\":" + page + ",\"totalElements\":0,\"totalPages\":0}");
        }

        @Override
        public ResponseEntity<String> resolveDeviceAssignment(String deviceRef) {
            return json(200, "{\"assignmentId\":\"A-1\",\"deviceRef\":\"" + deviceRef
                    + "\",\"status\":\"ACTIVE\",\"calibrationPosture\":\"DEGRADED\","
                    + "\"calibrationPostureReason\":\"Calibration lapsed 2026-06-30\"}");
        }

        private static ResponseEntity<String> json(int status, String body) {
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
        }
    }

    private static final class FailingClient extends TelemonitoringServiceClient {
        private final RuntimeException failure;

        FailingClient(RuntimeException failure) {
            super(new RestTemplate(), endpoints());
            this.failure = failure;
        }

        @Override
        public ResponseEntity<String> listAlertEpisodes(String planId, String status, String deviceRef) {
            throw failure;
        }

        @Override
        public ResponseEntity<String> resolveEpisode(String episodeId, String body, String idempotencyKey) {
            throw failure;
        }
    }
}
