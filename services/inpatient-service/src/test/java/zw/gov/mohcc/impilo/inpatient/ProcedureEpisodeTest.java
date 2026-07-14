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

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProcedureEpisodeTest {

    private static final String CPID = "CPID-ZW-00001";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withTrust(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        return b.header("X-Tenant-ID", "00000000-0000-4000-8000-000000000001")
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", "req-procedure")
                .header("X-Correlation-ID", "corr-procedure")
                .header("Idempotency-Key", "idem-" + UUID.randomUUID());
    }

    @Test
    void full_perioperative_pipeline() throws Exception {
        String createBody = mockMvc.perform(withTrust(post("/internal/v1/procedures/episodes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"" + CPID + "\",\"procedureName\":\"Laparoscopic appendectomy\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.procedure_name").value("Laparoscopic appendectomy"))
                .andExpect(jsonPath("$.checklist", hasSize(12)))
                .andReturn().getResponse().getContentAsString();
        String episodeId = MAPPER.readTree(createBody).get("id").asText();

        mockMvc.perform(withTrust(post("/internal/v1/procedures/episodes/" + episodeId + "/preop"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assessmentType\":\"NURSING\",\"fastingVerified\":true,\"allergiesReviewed\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(withTrust(post("/internal/v1/procedures/episodes/" + episodeId + "/preop"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assessmentType\":\"ANAESTHESIA\",\"asaClass\":\"II\","
                                + "\"mallampatiClass\":\"II\",\"anaesthetistNotes\":\"GA planned\","
                                + "\"clearedForTheatre\":true,"
                                + "\"scores\":[{\"scoreType\":\"APFEL\",\"components\":{\"female\":true,\"postopOpioids\":true}}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anaesthesia_scores").isArray())
                .andExpect(jsonPath("$.anaesthesia_scores.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        String detail = mockMvc.perform(withTrust(get("/internal/v1/procedures/episodes/" + episodeId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode checklist = MAPPER.readTree(detail).get("checklist");
        StringBuilder signInIds = new StringBuilder("[");
        for (JsonNode item : checklist) {
            if ("SIGN_IN".equals(item.get("phase").asText())) {
                if (signInIds.length() > 1) signInIds.append(",");
                signInIds.append("\"").append(item.get("id").asText()).append("\"");
            }
        }
        signInIds.append("]");

        mockMvc.perform(withTrust(post("/internal/v1/procedures/episodes/" + episodeId + "/checklist/SIGN_IN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemIds\":" + signInIds + ",\"completedBy\":\"nurse\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(withTrust(post("/internal/v1/procedures/episodes/" + episodeId + "/consent-evidence"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mvumoConsentRequestId\":\"a1000000-0000-0000-0000-000000000099\","
                                + "\"tshepoConsentId\":\"a1000000-0000-0000-0000-000000000098\","
                                + "\"consentProofRef\":\"tshepo-consent:a1000000-0000-0000-0000-000000000098\","
                                + "\"consentStatus\":\"GRANTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consent_status").value("GRANTED"));

        mockMvc.perform(withTrust(post("/internal/v1/procedures/episodes/" + episodeId + "/start"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(withTrust(post("/internal/v1/procedures/episodes/" + episodeId + "/intraop/events"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"NOTE\",\"description\":\"Appendix removed\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(withTrust(post("/internal/v1/procedures/episodes/" + episodeId + "/pacu"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PACU"));

        mockMvc.perform(withTrust(post("/internal/v1/procedures/episodes/" + episodeId + "/postop"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aldreteScore\":9,\"painScore\":2,\"disposition\":\"WARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECOVERED"));

        mockMvc.perform(withTrust(post("/internal/v1/procedures/episodes/" + episodeId + "/complete"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(withTrust(get("/internal/v1/procedures/episodes/history?patient_id=" + CPID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Laparoscopic appendectomy"));
    }
}
