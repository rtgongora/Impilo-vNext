package zw.gov.mohcc.impilo.inpatient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end theatre & perioperative depth journey on the H2 schema. Downstream owners (OROS, varapi,
 * vashandi, asset-registry, Madi, PCT, Butano) are NOT running, so every owner-routed call degrades to
 * UNAVAILABLE/null — proving the no-fake guarantee: booking is BLOCKED with owner blockers, death routes
 * with routed_status=OWNER_UNAVAILABLE, and the note still signs locally.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TheatreDepthTest {

    private static final String CPID = "CPID-ZW-THEATRE-1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private static MockHttpServletRequestBuilder withTrust(MockHttpServletRequestBuilder b) {
        return b.header("X-Tenant-ID", "00000000-0000-4000-8000-000000000001")
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", "req-theatre")
                .header("X-Correlation-ID", "corr-theatre")
                .header("X-Facility-ID", "00000000-0000-4000-8000-0000000000fa")
                .header("X-Actor-ID", "surgeon-1")
                .header("Idempotency-Key", "idem-" + UUID.randomUUID());
    }

    @Test
    void theatre_intake_triage_booking_blockers_note_death() throws Exception {
        // 1. OROS PROCEDURE intake → theatre case (OROS down → oros_order_id null, case still created)
        String created = mockMvc.perform(withTrust(post("/internal/v1/theatre/cases"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"" + CPID + "\",\"procedureName\":\"Open cholecystectomy\","
                                + "\"procedureCode\":\"0HBJ\",\"triagePriority\":\"URGENT\",\"surgeonId\":\"surgeon-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.procedure_name").value("Open cholecystectomy"))
                .andReturn().getResponse().getContentAsString();
        String caseId = MAPPER.readTree(created).get("id").asText();

        // 2. triage queue ranks the case
        mockMvc.perform(withTrust(get("/internal/v1/theatre/queue")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + caseId + "')].triage_priority").value(hasItem("URGENT")));

        // 3. readiness — no room, owners down → BLOCKED with owner blockers, NOT bookable, no fake READY
        String readiness = mockMvc.perform(withTrust(post("/internal/v1/theatre/cases/" + caseId + "/readiness"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookable").value(false))
                .andExpect(jsonPath("$.blockers", not(empty())))
                .andReturn().getResponse().getContentAsString();
        JsonNode checks = MAPPER.readTree(readiness).get("checks");
        boolean hasRoomBlock = false;
        for (JsonNode c : checks) {
            if ("ROOM".equals(c.get("domain").asText()) && "BLOCKED".equals(c.get("status").asText())) hasRoomBlock = true;
            // no check must be fabricated READY for a down owner
            if ("EQUIPMENT".equals(c.get("domain").asText())) {
                org.junit.jupiter.api.Assertions.assertNotEquals("READY", c.get("status").asText(),
                        "asset-registry is down — equipment must not be fabricated READY");
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(hasRoomBlock, "room blocker expected");

        // 4. booking blocked (409) without override
        mockMvc.perform(withTrust(post("/internal/v1/theatre/cases/" + caseId + "/book"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_BLOCKED"))
                .andExpect(jsonPath("$.blockers", not(empty())));

        // 5. emergency override books with reason (audited)
        mockMvc.perform(withTrust(post("/internal/v1/theatre/cases/" + caseId + "/book"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emergencyOverride\":true,\"emergencyOverrideReason\":\"Ruptured viscus, life-threatening\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking_override").value(true));

        // 6. WHO checklist incomplete → start blocked (409) unless override
        mockMvc.perform(withTrust(post("/internal/v1/theatre/cases/" + caseId + "/start"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());

        // override + consent path: bind consent then start with override
        mockMvc.perform(withTrust(post("/internal/v1/procedures/episodes/" + caseId + "/consent-evidence"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mvumoConsentRequestId\":\"a1000000-0000-0000-0000-000000000011\","
                                + "\"consentStatus\":\"GRANTED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(withTrust(post("/internal/v1/theatre/cases/" + caseId + "/start"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emergencyOverride\":true,\"emergencyOverrideReason\":\"Emergency theatre\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // 7. operative note draft → sign (Butano down → local sign succeeds, butano refs null)
        mockMvc.perform(withTrust(post("/internal/v1/theatre/cases/" + caseId + "/note"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedProcedure\":\"Open cholecystectomy\",\"findings\":\"Gangrenous gallbladder\","
                                + "\"estimatedBloodLossMl\":150,\"countsCorrect\":true,\"postopPlan\":\"IV abx, HDU\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(withTrust(post("/internal/v1/theatre/cases/" + caseId + "/note/sign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signedProviderId\":\"surgeon-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.signed_provider_id").value("surgeon-1"));

        // 8. safety event → routed to Rito (owner) — recorded, no fake Rito record
        mockMvc.perform(withTrust(post("/internal/v1/theatre/cases/" + caseId + "/safety-events"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"NEAR_MISS\",\"severity\":\"MODERATE\",\"description\":\"Swab count delay\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routed_owner").value("rito"))
                .andExpect(jsonPath("$.routed_status").value("ROUTED"));

        // 9. death-in-theatre → PCT death pathway (PCT down → OWNER_UNAVAILABLE, episode DECEASED)
        mockMvc.perform(withTrust(post("/internal/v1/theatre/cases/" + caseId + "/death"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resuscitationAttempted\":true,\"suspectedManner\":\"NATURAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECEASED"))
                .andExpect(jsonPath("$.death_routed").value(false));

        // death is recorded as a SENTINEL safety event routed to pct-death
        mockMvc.perform(withTrust(get("/internal/v1/theatre/cases/" + caseId + "/safety-events")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.category=='DEATH')].routed_owner").value(hasItem("pct-death")))
                .andExpect(jsonPath("$[?(@.category=='DEATH')].routed_status").value(hasItem("OWNER_UNAVAILABLE")));
    }

    @Test
    void note_sign_requires_provider_id() throws Exception {
        String created = mockMvc.perform(withTrust(post("/internal/v1/theatre/cases"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"" + CPID + "-2\",\"procedureName\":\"Hernia repair\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String caseId = MAPPER.readTree(created).get("id").asText();
        mockMvc.perform(withTrust(post("/internal/v1/theatre/cases/" + caseId + "/note"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedProcedure\":\"Hernia repair\"}"))
                .andExpect(status().isCreated());
        // sign without signedProviderId → 400
        mockMvc.perform(withTrust(post("/internal/v1/theatre/cases/" + caseId + "/note/sign"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancellation_requires_reason_and_releases() throws Exception {
        String created = mockMvc.perform(withTrust(post("/internal/v1/theatre/cases"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"" + CPID + "-3\",\"procedureName\":\"Cataract\",\"triagePriority\":\"DAY_CASE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String caseId = MAPPER.readTree(created).get("id").asText();
        // cancel without reason → 400
        mockMvc.perform(withTrust(post("/internal/v1/theatre/cases/" + caseId + "/cancel"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        // cancel with reason → CANCELLED
        mockMvc.perform(withTrust(post("/internal/v1/theatre/cases/" + caseId + "/cancel"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Patient not fasted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
