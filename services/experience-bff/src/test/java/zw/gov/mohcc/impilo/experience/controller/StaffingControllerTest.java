package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.experience.staffing.StaffIdentityResolver;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Staffing surfaces after the repoint off tuso.
 *
 * <p>The handlers called {@code tuso /v1/staffing/*}, which no service serves. Rostering is
 * vashandi's, so the failure code and the upstream both change — and an empty week must now be
 * distinguishable from a broken one, which the previous implementation could not do.</p>
 */
class StaffingControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void rosterWeekReturnsBadGatewayWhenVashandiUnavailable() {
        StaffingController controller = controllerWith(new FailingVashandiClient());

        ResponseEntity<Map<String, Object>> response = controller.rosterWeek(
                "tenant-1", "req-1", "corr-1", "fac-1", "2026-05-11", null);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("VASHANDI_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void onCallReturnsBadGatewayWhenVashandiUnavailable() {
        StaffingController controller = controllerWith(new FailingVashandiClient());

        ResponseEntity<Map<String, Object>> response = controller.listOnCall(
                "tenant-1", "req-2", "corr-2", "fac-1", "2026-05-11");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("VASHANDI_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    /**
     * The defect this closes: the previous handlers returned {@code data: []} both when the
     * upstream answered with an empty list and when it answered with nothing at all, so "no shifts
     * rostered this week" and "the call produced nothing" were indistinguishable on screen. A
     * genuinely empty week is a 200 with an empty array; a failure is a 502.
     */
    @Test
    void anEmptyWeekIsAnEmptyWeekNotAFailure() {
        StaffingController controller = controllerWith(new EmptyVashandiClient());

        ResponseEntity<Map<String, Object>> response = controller.rosterWeek(
                "tenant-1", "req-3", "corr-3", "fac-1", "2026-05-11", null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("data") instanceof JsonNode node && node.isArray()
                        && node.isEmpty(),
                "an empty roster must surface as an empty array, not as an upstream failure");
    }

    @Test
    void swapDecisionRejectsAnUnknownStatus() {
        StaffingController controller = controllerWith(new EmptyVashandiClient());

        try {
            controller.patchSwap(java.util.UUID.randomUUID(), "tenant-1", "req-4", "corr-4",
                    new StaffingController.PatchSwapRequest("MAYBE", null));
            org.junit.jupiter.api.Assertions.fail("an unknown decision must be refused");
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            assertEquals(400, ex.getStatusCode().value());
        }
    }

    // ── display-name composition ─────────────────────────────────────────────

    /**
     * The point of the composition: vashandi sends a profile id and a worker reference, and the
     * screen shows the person's name and the number the provider registry holds for them.
     */
    @Test
    void theRotaShowsResolvedNamesAndTheRegistryPhone() {
        StaffingController controller = controllerWith(
                new RotaVashandiClient(), resolvedVarapi(HEALTH_ID_A, "Dr Tendai Moyo", "+263771234567"));

        ResponseEntity<Map<String, Object>> response = controller.listOnCall(
                "tenant-1", "req-5", "corr-5", "fac-1", "2026-05-11");

        JsonNode attributes = firstRowAttributes(response);
        assertEquals("Dr Tendai Moyo", attributes.get("primary_display_name").asText());
        assertEquals("+263771234567", attributes.get("primary_phone").asText());
        assertEquals("PROVIDER_REGISTRY", attributes.get("primary_phone_source").asText());
        assertEquals("LIVE", ((Map<?, ?>) response.getBody().get("meta")).get("identity_resolution"));
    }

    /** The reference survives resolution: it is what staff recognise, and what a failure falls back to. */
    @Test
    void theWorkerReferenceIsKeptAlongsideTheResolvedName() {
        StaffingController controller = controllerWith(
                new RotaVashandiClient(), resolvedVarapi(HEALTH_ID_A, "Dr Tendai Moyo", "+263771234567"));

        JsonNode attributes = firstRowAttributes(controller.listOnCall(
                "tenant-1", "req-6", "corr-6", "fac-1", "2026-05-11"));

        assertEquals("EC-4471", attributes.get("primary_staff_reference").asText());
    }

    /**
     * The failure this exists to prevent. When a registry cannot be reached the rota still renders
     * from the references it already has — but it must not look like a rota of people who simply
     * have no names on file, or nobody notices the registries are down.
     */
    @Test
    void aRegistryThatDoesNotAnswerIsReportedNotRenderedAsNamelessStaff() {
        StaffingController controller = controllerWith(new RotaVashandiClient(), new FailingVarapiClient());

        ResponseEntity<Map<String, Object>> response = controller.listOnCall(
                "tenant-1", "req-7", "corr-7", "fac-1", "2026-05-11");

        assertEquals(200, response.getStatusCode().value(), "an unresolvable name must not lose the rota");
        JsonNode attributes = firstRowAttributes(response);
        assertEquals("EC-4471", attributes.get("primary_staff_reference").asText());
        assertTrue(attributes.get("primary_display_name").isNull());
        assertEquals("UNAVAILABLE", ((Map<?, ?>) response.getBody().get("meta")).get("identity_resolution"));
    }

    /**
     * A person the provider registry does not know is a finding, not a failure — resolution stayed
     * live, and no phone number was conjured for them.
     */
    @Test
    void somebodyMissingFromTheRegistryResolvesToNoNameAndNoNumber() {
        StaffingController controller = controllerWith(
                new RotaVashandiClient(), resolvedVarapi("some-other-health-id", "Dr Someone Else", "+263770000000"));

        ResponseEntity<Map<String, Object>> response = controller.listOnCall(
                "tenant-1", "req-8", "corr-8", "fac-1", "2026-05-11");

        JsonNode attributes = firstRowAttributes(response);
        assertTrue(attributes.get("primary_display_name").isNull());
        assertTrue(attributes.get("primary_phone").isNull());
        assertFalse(attributes.has("primary_phone_source"));
        assertEquals("LIVE", ((Map<?, ?>) response.getBody().get("meta")).get("identity_resolution"));
    }

    /** An uncovered night stays uncovered: resolution must not invent a second on call. */
    @Test
    void anUncoveredNightIsStillUncoveredAfterResolution() {
        StaffingController controller = controllerWith(
                new RotaVashandiClient(), resolvedVarapi(HEALTH_ID_A, "Dr Tendai Moyo", "+263771234567"));

        JsonNode attributes = firstRowAttributes(controller.listOnCall(
                "tenant-1", "req-9", "corr-9", "fac-1", "2026-05-11"));

        assertTrue(attributes.get("backup_workforce_profile_id").isNull());
        assertTrue(attributes.get("backup_display_name").isNull());
        assertTrue(attributes.get("backup_phone").isNull());
    }

    /** With nobody rostered there is nothing to ask the registries, and that is its own state. */
    @Test
    void anEmptyWeekAsksTheRegistriesNothing() {
        StaffingController controller = controllerWith(new EmptyVashandiClient());

        ResponseEntity<Map<String, Object>> response = controller.listOnCall(
                "tenant-1", "req-10", "corr-10", "fac-1", "2026-05-11");

        assertEquals("NOT_APPLICABLE", ((Map<?, ?>) response.getBody().get("meta")).get("identity_resolution"));
    }

    @Test
    void swapRequestsCarryBothPartiesResolvedNames() {
        StaffingController controller = controllerWith(
                new SwapsVashandiClient(), resolvedVarapi(HEALTH_ID_A, "Dr Tendai Moyo", "+263771234567"));

        ResponseEntity<Map<String, Object>> response = controller.listSwaps(
                "tenant-1", "req-11", "corr-11", "fac-1");

        JsonNode attributes = firstRowAttributes(response);
        assertEquals("Dr Tendai Moyo", attributes.get("requesting_display_name").asText());
        assertTrue(attributes.get("requested_display_name").isNull(),
                "the other party is not in the registry fixture and must not acquire a name");
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static final String PROFILE_A = "11111111-1111-1111-1111-111111111111";
    private static final String PROFILE_B = "22222222-2222-2222-2222-222222222222";
    private static final String HEALTH_ID_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    private static JsonNode firstRowAttributes(ResponseEntity<Map<String, Object>> response) {
        assertNotNull(response.getBody());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertNotNull(data);
        return data.get(0).get("attributes");
    }

    private static StaffingController controllerWith(VashandiServiceClient vashandi) {
        return controllerWith(vashandi, new FailingVarapiClient());
    }

    private static StaffingController controllerWith(VashandiServiceClient vashandi, VarapiServiceClient varapi) {
        return new StaffingController(vashandi, new StaffIdentityResolver(vashandi, varapi));
    }

    private static VarapiServiceClient resolvedVarapi(String healthId, String displayName, String phone) {
        return new VarapiServiceClient(new RestTemplate(), endpoints()) {
            @Override
            public JsonNode resolveDisplayFacts(List<String> healthIds) {
                ArrayNode rows = MAPPER.createArrayNode();
                for (String requested : healthIds) {
                    ObjectNode row = rows.addObject();
                    row.put("healthId", requested);
                    boolean known = requested.equals(healthId);
                    row.put("resolved", known);
                    row.put("displayName", known ? displayName : null);
                    row.put("phone", known ? phone : null);
                    row.put("profession", known ? "MEDICAL_PRACTITIONER" : null);
                    row.put("cadre", known ? "REGISTRAR" : null);
                }
                return rows;
            }
        };
    }

    private static final class FailingVarapiClient extends VarapiServiceClient {
        private FailingVarapiClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode resolveDisplayFacts(List<String> healthIds) {
            throw new RuntimeException("varapi unavailable");
        }
    }

    /** One night with a first call and nobody on second, exactly as vashandi projects it. */
    private static class RotaVashandiClient extends VashandiServiceClient {
        private RotaVashandiClient() {
            super(new RestTemplate(), endpoints(), MAPPER);
        }

        @Override
        public JsonNode listOnCall(String facilityId, String weekStart) {
            ArrayNode rows = MAPPER.createArrayNode();
            ObjectNode row = rows.addObject();
            row.put("id", "2026-05-11|SURGERY");
            row.put("type", "on-call-assignment");
            ObjectNode attributes = row.putObject("attributes");
            attributes.put("assignment_date", "2026-05-11");
            attributes.put("specialty", "SURGERY");
            attributes.put("shift_kind", "NIGHT");
            attributes.put("primary_workforce_profile_id", PROFILE_A);
            attributes.put("primary_staff_reference", "EC-4471");
            attributes.put("primary_shift_id", "shift-1");
            attributes.putNull("primary_phone");
            attributes.putNull("backup_workforce_profile_id");
            attributes.putNull("backup_staff_reference");
            attributes.putNull("backup_shift_id");
            attributes.putNull("backup_phone");
            return rows;
        }

        @Override
        public JsonNode getWorkforceIdentityRefs(String profileIdsCsv) {
            ArrayNode rows = MAPPER.createArrayNode();
            for (String profileId : profileIdsCsv.split(",")) {
                ObjectNode row = rows.addObject();
                row.put("workforce_profile_id", profileId);
                row.put("health_id", PROFILE_A.equals(profileId) ? HEALTH_ID_A : "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
                row.put("provider_worker_id", "EC-4471");
            }
            return rows;
        }
    }

    private static final class SwapsVashandiClient extends RotaVashandiClient {
        @Override
        public JsonNode listSwapRequests(String facilityId) {
            ArrayNode rows = MAPPER.createArrayNode();
            ObjectNode row = rows.addObject();
            row.put("id", "swap-1");
            row.put("type", "shift-swap-request");
            ObjectNode attributes = row.putObject("attributes");
            attributes.put("requesting_shift_id", "shift-1");
            attributes.put("requesting_workforce_profile_id", PROFILE_A);
            attributes.put("requested_workforce_profile_id", PROFILE_B);
            attributes.put("status", "PENDING");
            return rows;
        }
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class FailingVashandiClient extends VashandiServiceClient {
        private FailingVashandiClient() {
            super(new RestTemplate(), endpoints(), MAPPER);
        }

        @Override
        public JsonNode getRosterWeek(String facilityId, String weekStart) {
            throw new RuntimeException("vashandi unavailable");
        }

        @Override
        public JsonNode listOnCall(String facilityId, String weekStart) {
            throw new RuntimeException("vashandi unavailable");
        }
    }

    private static final class EmptyVashandiClient extends VashandiServiceClient {
        private EmptyVashandiClient() {
            super(new RestTemplate(), endpoints(), MAPPER);
        }

        @Override
        public JsonNode getRosterWeek(String facilityId, String weekStart) {
            return MAPPER.createArrayNode();
        }

        @Override
        public JsonNode listOnCall(String facilityId, String weekStart) {
            return MAPPER.createArrayNode();
        }
    }
}
