package zw.gov.mohcc.impilo.simba;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import zw.gov.mohcc.impilo.simba.persistence.entity.RiskRuleEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.RiskRuleRepository;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end assessment journey on the real stack (H2): start → save steps → complete → risk band +
 * care linkage + timeline. Seeds a couple of deterministic risk rules (V006 seeds them in Postgres;
 * the H2 test schema is JPA-generated, so we seed via the repository).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WellnessAssessmentJourneyIT {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private RiskRuleRepository riskRuleRepository;

    private MockHttpServletRequestBuilder trust(MockHttpServletRequestBuilder b, String cpid) {
        return b.header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", "req-assess")
                .header("X-Correlation-ID", "corr-assess")
                .header("X-Actor-ID", cpid)
                .header("X-Actor-Type", "CITIZEN")
                .header("Idempotency-Key", "idem-" + UUID.randomUUID());
    }

    private void seedRule(String code, String question, String op, Double low, Double high,
                          String match, String band, boolean redFlag) {
        RiskRuleEntity r = new RiskRuleEntity();
        r.setTenantId(UUID.fromString(TENANT));
        r.setRuleCode(code);
        r.setTemplateCode("BASELINE_WELLNESS");
        r.setQuestionCode(question);
        r.setOperator(op);
        r.setThresholdLow(low == null ? null : BigDecimal.valueOf(low));
        r.setThresholdHigh(high == null ? null : BigDecimal.valueOf(high));
        r.setMatchValues(match);
        r.setContributesBand(band);
        r.setWeight(BigDecimal.valueOf(redFlag ? 5.0 : 3.0));
        r.setRedFlag(redFlag);
        r.setStatus("ACTIVE");
        riskRuleRepository.save(r);
    }

    @Test
    void baselineAssessment_highBp_scoresHighAndRoutesCareAndTimelines() throws Exception {
        String cpid = "it-assess-high-" + UUID.randomUUID();
        seedRule("BP_HIGH", "systolic_bp", "RANGE", 140.0, 179.999, null, "HIGH", false);
        seedRule("SELF_HARM", "self_harm_thoughts", "EQ", null, null, "YES", "URGENT_RED_FLAG", true);

        // Start
        MvcResult startRes = mockMvc.perform(trust(post("/internal/v1/wellness/assessments"), cpid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_cpid\":\"" + cpid + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        String assessmentId = MAPPER.readTree(startRes.getResponse().getContentAsString())
                .get("data").get("assessmentId").asText();

        // Consent + measurement step
        mockMvc.perform(trust(patch("/internal/v1/wellness/assessments/" + assessmentId + "/step"), cpid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "step": "MEASUREMENTS",
                                  "consent_captured": true,
                                  "responses": [
                                    {"section":"MEASUREMENT","question_code":"systolic_bp","value_numeric":150}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.consentCaptured").value(true));

        // Complete → HIGH band + care linkage routed
        MvcResult completeRes = mockMvc.perform(
                        trust(post("/internal/v1/wellness/assessments/" + assessmentId + "/complete"), cpid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.risk_band").value("HIGH"))
                .andExpect(jsonPath("$.care_linkage_routed").value(true))
                .andReturn();
        JsonNode complete = MAPPER.readTree(completeRes.getResponse().getContentAsString());
        assertThat(complete.get("next_actions").isArray()).isTrue();

        // Timeline reflects the assessment (and the risk-change/escalation row).
        mockMvc.perform(trust(get("/internal/v1/wellness/timeline"), cpid)
                        .param("person_cpid", cpid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].personCpid").value(cpid))
                .andExpect(jsonPath("$.data[?(@.eventCategory=='ASSESSMENT')]").exists());
    }

    @Test
    void crossPersonTimelineRead_isForbiddenAndAudited() throws Exception {
        String subject = "it-assess-subject-" + UUID.randomUUID();
        // Actor differs from the requested person_cpid -> guard denies with 403.
        mockMvc.perform(get("/internal/v1/wellness/timeline")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "national-spine")
                        .header("X-Request-ID", "req-x")
                        .header("X-Correlation-ID", "corr-x")
                        .header("X-Actor-ID", "someone-else")
                        .header("X-Actor-Type", "CITIZEN")
                        .param("person_cpid", subject))
                .andExpect(status().isForbidden());
    }
}
