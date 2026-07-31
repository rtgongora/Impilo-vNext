package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * mm-compose (brief.md §9): proves the BFF actually assembles appointments, investigations and
 * functional limitations onto the wire CKP reads — not merely that the fields exist somewhere in
 * source, but that a stubbed downstream response ends up as the exact key/shape CKP's
 * {@code MultimorbidityController} parses ({@code date}/{@code clinic}/{@code reason},
 * {@code code}/{@code date}/{@code orderedBy}, and plain description strings).
 *
 * <p>Also proves the omit-never-empty rule at each of the three new sources independently: a
 * downstream failure removes the key rather than sending {@code []}, exactly like the pre-existing
 * {@code conditions} behaviour this pack did not touch.</p>
 */
class MultimorbidityControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void cancelledAndUndatedAppointmentsAreDroppedButLiveOnesCarryDateClinicAndReason() {
        CapturingCkp ckp = new CapturingCkp();
        MultimorbidityController controller = controller(ckp, new EmptyPct(), new EmptyOros(),
                new StubBooking(List.of(
                        appointment("BOOKED", "2026-06-01T09:00:00Z", "Chikurubi Clinic", "Follow-up"),
                        appointment("CANCELLED", "2026-06-15T09:00:00Z", "Chikurubi Clinic", "Review"),
                        appointment("BOOKED", null, "Chikurubi Clinic", "No date")
                )));

        controller.assess("CPID-1", "req-1", "corr-1");

        JsonNode appointments = ckp.lastBody.get("appointments");
        assertEquals(1, appointments.size());
        assertEquals("2026-06-01", appointments.get(0).get("date").asText());
        assertEquals("Chikurubi Clinic", appointments.get(0).get("clinic").asText());
        assertEquals("Follow-up", appointments.get(0).get("reason").asText());
    }

    @Test
    void onlyLabAndImagingOrdersBecomeInvestigationsWithCodeDateAndOrderedBy() {
        CapturingCkp ckp = new CapturingCkp();
        MultimorbidityController controller = controller(ckp, new EmptyPct(),
                new StubOros(List.of(
                        order("LAB", "2026-05-01T08:00:00Z", "FBC", "Dr Moyo"),
                        order("PHARMACY", "2026-05-02T08:00:00Z", "PARA-500", "Dr Moyo"),
                        order("IMAGING", null, "CXR", "Dr Moyo")
                )), new EmptyBooking());

        controller.assess("CPID-1", "req-1", "corr-1");

        JsonNode investigations = ckp.lastBody.get("investigations");
        assertEquals(1, investigations.size());
        assertEquals("FBC", investigations.get(0).get("code").asText());
        assertEquals("2026-05-01", investigations.get(0).get("date").asText());
        assertEquals("Dr Moyo", investigations.get(0).get("orderedBy").asText());
    }

    @Test
    void functionalAssessmentsRenderAsScoredOrNotAssessableDescriptions() {
        CapturingCkp ckp = new CapturingCkp();
        MultimorbidityController controller = controller(ckp,
                new StubPct(List.of(
                        functional("ECOG", 2, 4, "Ambulatory", null),
                        functional("BARTHEL", null, null, null, "patient too unwell to test")
                )), new EmptyOros(), new EmptyBooking());

        controller.assess("CPID-1", "req-1", "corr-1");

        JsonNode limitations = ckp.lastBody.get("functionalLimitations");
        assertEquals(2, limitations.size());
        assertEquals("ECOG 2/4 (Ambulatory)", limitations.get(0).asText());
        assertEquals("BARTHEL: not assessable (patient too unwell to test)", limitations.get(1).asText());
    }

    @Test
    void patientPrioritiesAndCareTeamAreNeverSentBecauseNoSourceOwnsThemYet() {
        CapturingCkp ckp = new CapturingCkp();
        MultimorbidityController controller = controller(ckp, new EmptyPct(), new EmptyOros(), new EmptyBooking());

        controller.assess("CPID-1", "req-1", "corr-1");

        assertFalse(ckp.lastBody.has("patientPriorities"));
        assertFalse(ckp.lastBody.has("careTeam"));
    }

    @Test
    void aFailedAppointmentReadOmitsTheKeyRatherThanSendingAnEmptyArray() {
        CapturingCkp ckp = new CapturingCkp();
        MultimorbidityController controller = controller(ckp, new EmptyPct(), new EmptyOros(),
                new FailingBooking());

        controller.assess("CPID-1", "req-1", "corr-1");

        assertFalse(ckp.lastBody.has("appointments"));
    }

    @Test
    void aFailedInvestigationReadOmitsTheKeyRatherThanSendingAnEmptyArray() {
        CapturingCkp ckp = new CapturingCkp();
        MultimorbidityController controller = controller(ckp, new EmptyPct(), new FailingOros(),
                new EmptyBooking());

        controller.assess("CPID-1", "req-1", "corr-1");

        assertFalse(ckp.lastBody.has("investigations"));
    }

    @Test
    void aFailedFunctionalAssessmentReadOmitsTheKeyRatherThanSendingAnEmptyArray() {
        CapturingCkp ckp = new CapturingCkp();
        MultimorbidityController controller = controller(ckp, new FailingPct(), new EmptyOros(),
                new EmptyBooking());

        controller.assess("CPID-1", "req-1", "corr-1");

        assertFalse(ckp.lastBody.has("functionalLimitations"));
    }

    @Test
    void nothingCleanlyEmptyIsSentWhenEveryNewSourceHasNothingToOffer() {
        CapturingCkp ckp = new CapturingCkp();
        MultimorbidityController controller = controller(ckp, new EmptyPct(), new EmptyOros(), new EmptyBooking());

        controller.assess("CPID-1", "req-1", "corr-1");

        assertTrue(ckp.lastBody.get("appointments").isArray());
        assertEquals(0, ckp.lastBody.get("appointments").size());
        assertNull(ckp.lastBody.get("appointments").get(0));
    }

    // ── fixtures ───────────────────────────────────────────────────────

    private static MultimorbidityController controller(ClinicalKnowledgePlatformClient ckp, PctServiceClient pct,
                                                        OrosServiceClient oros, BookingServiceClient booking) {
        return new MultimorbidityController(ckp, pct, oros, booking, MAPPER);
    }

    private static ObjectNode appointment(String status, String scheduledAt, String facilityName, String reason) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("status", status);
        if (scheduledAt != null) {
            n.put("scheduled_at", scheduledAt);
        }
        n.put("facility_name", facilityName);
        n.put("reason", reason);
        return n;
    }

    private static ObjectNode order(String orderType, String placedAt, String testCode, String placedBy) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("orderType", orderType);
        if (placedAt != null) {
            n.put("placedAt", placedAt);
        }
        n.put("testCode", testCode);
        n.put("placedBy", placedBy);
        return n;
    }

    private static ObjectNode functional(String type, Integer total, Integer max, String interpretation,
                                         String absentReason) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("assessmentType", type);
        if (total != null) {
            n.put("totalScore", total);
        }
        if (max != null) {
            n.put("maxScore", max);
        }
        if (interpretation != null) {
            n.put("interpretation", interpretation);
        }
        if (absentReason != null) {
            n.put("scoreAbsentReason", absentReason);
        }
        return n;
    }

    // ── stubs ───────────────────────────────────────────────────────────

    private static class CapturingCkp extends ClinicalKnowledgePlatformClient {
        JsonNode lastBody;

        CapturingCkp() {
            super(new RestTemplate(), new ClinicalPlatformProperties("http://ckp.test"));
        }

        @Override
        public JsonNode multimorbidityAssess(Object body) {
            this.lastBody = (JsonNode) body;
            ObjectNode out = MAPPER.createObjectNode();
            out.putArray("findings");
            return out;
        }
    }

    private static class EmptyPct extends PctServiceClient {
        EmptyPct() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), MAPPER);
        }

        @Override
        public JsonNode listProblems(String subjectCpid) {
            return MAPPER.createArrayNode();
        }

        @Override
        public JsonNode getFunctionalAssessments(String patientCpid) {
            return MAPPER.createArrayNode();
        }
    }

    private static class StubPct extends EmptyPct {
        private final List<ObjectNode> assessments;

        StubPct(List<ObjectNode> assessments) {
            this.assessments = assessments;
        }

        @Override
        public JsonNode getFunctionalAssessments(String patientCpid) {
            ArrayNode arr = MAPPER.createArrayNode();
            assessments.forEach(arr::add);
            return arr;
        }
    }

    private static class FailingPct extends EmptyPct {
        @Override
        public JsonNode getFunctionalAssessments(String patientCpid) {
            throw new RuntimeException("pct down");
        }
    }

    private static class EmptyOros extends OrosServiceClient {
        EmptyOros() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public List<String> currentMedicationCodes(String subjectCpid) {
            return List.of();
        }

        @Override
        public JsonNode getPatientOrders(String cpid) {
            return MAPPER.createArrayNode();
        }
    }

    private static class StubOros extends EmptyOros {
        private final List<ObjectNode> orders;

        StubOros(List<ObjectNode> orders) {
            this.orders = orders;
        }

        @Override
        public JsonNode getPatientOrders(String cpid) {
            ArrayNode arr = MAPPER.createArrayNode();
            orders.forEach(arr::add);
            return arr;
        }
    }

    private static class FailingOros extends EmptyOros {
        @Override
        public JsonNode getPatientOrders(String cpid) {
            throw new RuntimeException("oros down");
        }
    }

    private static class EmptyBooking extends BookingServiceClient {
        EmptyBooking() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode listCitizenAppointments(String cpid, String status, int page, int size) {
            return MAPPER.createArrayNode();
        }
    }

    private static class StubBooking extends EmptyBooking {
        private final List<ObjectNode> appointments;

        StubBooking(List<ObjectNode> appointments) {
            this.appointments = appointments;
        }

        @Override
        public JsonNode listCitizenAppointments(String cpid, String status, int page, int size) {
            ArrayNode arr = MAPPER.createArrayNode();
            appointments.forEach(arr::add);
            return arr;
        }
    }

    private static class FailingBooking extends EmptyBooking {
        @Override
        public JsonNode listCitizenAppointments(String cpid, String status, int page, int size) {
            throw new RuntimeException("booking down");
        }
    }
}
