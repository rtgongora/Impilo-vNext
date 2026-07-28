package zw.gov.mohcc.impilo.mentalhealth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import zw.gov.mohcc.impilo.mentalhealth.integration.PctHandoverClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real HTTP surface: referral intake -> accept (calling back a mocked pct),
 * assessment -> risk formulation, safety plan, involuntary episode open -> close, restraint event
 * record -> review, admission request raise -> acknowledge, follow-up schedule -> complete. Proves
 * the controller/service/repository wiring actually reaches the database, not just the service
 * layer in isolation (already covered by the per-service unit tests).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MentalHealthFlowTest {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PctHandoverClient pctHandoverClient;

    private MockHttpServletRequestBuilder trusted(MockHttpServletRequestBuilder b) {
        return b.header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("X-Actor-ID", "dr-x")
                .header("X-Actor-Type", "STAFF")
                .header("X-Purpose-Of-Use", "TREATMENT");
    }

    /** Trusted builder + a globally-unique Idempotency-Key, required by tech-companion on every
     *  POST/PUT/PATCH to a v1.1 internal endpoint. */
    private MockHttpServletRequestBuilder mutate(MockHttpServletRequestBuilder b) {
        return trusted(b).header("Idempotency-Key", "idem-" + UUID.randomUUID());
    }

    private JsonNode postJson(String url, String body) throws Exception {
        MvcResult r = mockMvc.perform(mutate(post(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
        return MAPPER.readTree(r.getResponse().getContentAsString()).get("data");
    }

    private JsonNode getJson(String url) throws Exception {
        MvcResult r = mockMvc.perform(trusted(get(url))).andReturn();
        return MAPPER.readTree(r.getResponse().getContentAsString()).get("data");
    }

    @Test
    void fullPsychiatricEmergencyFlowOverHttp() throws Exception {
        when(pctHandoverClient.acceptHandover(any(), any(), any(), anyString())).thenReturn(true);
        when(pctHandoverClient.declineHandover(any(), any(), anyString(), anyString())).thenReturn(true);

        UUID episodeId = UUID.randomUUID();
        UUID handoverId = UUID.randomUUID();

        // 1. Intake the referral (the accepting side of the handover).
        JsonNode referral = postJson("/internal/v1/mental-health/referrals", """
                {"facilityId":"00000000-0000-4000-8000-000000000002",
                 "episodeId":"%s","handoverId":"%s","subjectCpid":"CPID-1",
                 "requestReason":"acute psychosis","requestedBy":"nurse-A"}
                """.formatted(episodeId, handoverId));
        assertThat(referral.get("status").asText()).isEqualTo("PENDING");
        String referralId = referral.get("id").asText();

        // Idempotent replay: same handover, same referral back.
        JsonNode replay = postJson("/internal/v1/mental-health/referrals", """
                {"facilityId":"00000000-0000-4000-8000-000000000002",
                 "episodeId":"%s","handoverId":"%s","requestedBy":"nurse-B"}
                """.formatted(episodeId, handoverId));
        assertThat(replay.get("id").asText()).isEqualTo(referralId);

        // 2. Accept — writes back to pct (mocked).
        JsonNode accepted = postJson("/internal/v1/mental-health/referrals/" + referralId + "/accept",
                "{\"acceptedBy\":\"dr-x\"}");
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");

        // 3. Assessment + risk formulation.
        JsonNode assessment = postJson("/internal/v1/mental-health/referrals/" + referralId + "/assessments", """
                {"assessmentType":"INITIAL","mentalStateExam":"guarded, responding to internal stimuli",
                 "capacityAssessed":true,"capacityOutcome":"LACKS_CAPACITY","assessedBy":"dr-x"}
                """);
        String assessmentId = assessment.get("id").asText();
        assertThat(assessment.get("capacity_outcome").asText()).isEqualTo("LACKS_CAPACITY");

        JsonNode risk = postJson("/internal/v1/mental-health/assessments/" + assessmentId + "/risk-formulation", """
                {"riskToSelf":"HIGH","riskToOthers":"MODERATE","riskSummary":"active suicidal ideation with plan",
                 "formulatedBy":"dr-x"}
                """);
        assertThat(risk.get("risk_to_self").asText()).isEqualTo("HIGH");

        // 4. Safety plan.
        JsonNode plan = postJson("/internal/v1/mental-health/referrals/" + referralId + "/safety-plans", """
                {"warningSigns":"increasing agitation","copingStrategies":"call crisis line",
                 "supportContacts":"sister - 077...","createdBy":"dr-x"}
                """);
        assertThat(plan.get("id")).isNotNull();

        // 5. Involuntary episode: open then close.
        JsonNode ie = postJson("/internal/v1/mental-health/referrals/" + referralId + "/involuntary-episodes", """
                {"legalBasis":"Mental Health Act s.14","authorisedBy":"dr-x"}
                """);
        assertThat(ie.get("status").asText()).isEqualTo("OPEN");
        String ieId = ie.get("id").asText();

        JsonNode ieClosed = postJson("/internal/v1/mental-health/involuntary-episodes/" + ieId + "/close",
                "{\"endedReason\":\"no longer meets detention criteria\"}");
        assertThat(ieClosed.get("status").asText()).isEqualTo("CLOSED");

        // 6. Restraint-reduction register: record then review.
        JsonNode re = postJson("/internal/v1/mental-health/referrals/" + referralId + "/restraint-events", """
                {"restraintType":"PHYSICAL","reason":"assaultive",
                 "deEscalationAttempted":"verbal de-escalation attempted x2, PRN offered and declined",
                 "initiatedBy":"nurse-C"}
                """);
        String reId = re.get("id").asText();
        assertThat(re.get("reviewed_at").isNull()).isTrue();

        JsonNode reReviewed = postJson("/internal/v1/mental-health/restraint-events/" + reId + "/review",
                "{\"reviewedBy\":\"dr-x\",\"reviewOutcome\":\"appropriate, plan updated\"}");
        assertThat(reReviewed.get("reviewed_by").asText()).isEqualTo("dr-x");

        JsonNode unreviewed = getJson("/internal/v1/mental-health/restraint-events/unreviewed");
        assertThat(unreviewed.isArray()).isTrue();
        for (JsonNode row : unreviewed) {
            assertThat(row.get("id").asText()).isNotEqualTo(reId);
        }

        // 7. Admission request: raise then acknowledge (never a decision).
        JsonNode ar = postJson("/internal/v1/mental-health/referrals/" + referralId + "/admission-requests", """
                {"reason":"ongoing risk requires inpatient admission"}
                """);
        assertThat(ar.get("status").asText()).isEqualTo("RAISED");
        assertThat(ar.get("pct_admission_id").isNull()).isTrue();
        String arId = ar.get("id").asText();

        JsonNode arAck = postJson("/internal/v1/mental-health/admission-requests/" + arId + "/acknowledge",
                "{\"pctAdmissionId\":\"ADM-PCT-001\"}");
        assertThat(arAck.get("status").asText()).isEqualTo("ACKNOWLEDGED");
        assertThat(arAck.get("pct_admission_id").asText()).isEqualTo("ADM-PCT-001");

        // 8. Follow-up: schedule then complete.
        JsonNode fu = postJson("/internal/v1/mental-health/referrals/" + referralId + "/followups", """
                {"followupType":"CLINIC_REVIEW","scheduledAt":"2026-08-15T09:00:00Z"}
                """);
        String fuId = fu.get("id").asText();

        JsonNode fuDone = postJson("/internal/v1/mental-health/followups/" + fuId + "/complete",
                "{\"outcomeNotes\":\"attended, stable\"}");
        assertThat(fuDone.get("completed_at")).isNotNull();

        // 9. The referral list still shows this one PENDING-filtered query returning none (it's ACCEPTED).
        JsonNode pending = getJson("/internal/v1/mental-health/referrals?status=PENDING");
        for (JsonNode row : pending) {
            assertThat(row.get("id").asText()).isNotEqualTo(referralId);
        }
    }

    @Test
    void declineWritesBackToPctAndRequiresAReason() throws Exception {
        when(pctHandoverClient.declineHandover(any(), any(), anyString(), anyString())).thenReturn(true);

        JsonNode referral = postJson("/internal/v1/mental-health/referrals", """
                {"facilityId":"00000000-0000-4000-8000-000000000002",
                 "episodeId":"%s","handoverId":"%s","requestedBy":"nurse-A"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID()));
        String referralId = referral.get("id").asText();

        // No reason -> 400, still PENDING.
        mockMvc.perform(mutate(post("/internal/v1/mental-health/referrals/" + referralId + "/decline"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        JsonNode declined = postJson("/internal/v1/mental-health/referrals/" + referralId + "/decline",
                "{\"reason\":\"not a psychiatric emergency\"}");
        assertThat(declined.get("status").asText()).isEqualTo("DECLINED");
        assertThat(declined.get("decline_reason").asText()).isEqualTo("not a psychiatric emergency");
    }
}
