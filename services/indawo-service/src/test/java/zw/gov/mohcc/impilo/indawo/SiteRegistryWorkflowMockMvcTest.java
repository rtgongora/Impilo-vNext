package zw.gov.mohcc.impilo.indawo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SiteRegistryWorkflowMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = UUID.randomUUID().toString();

    @Test
    @DisplayName("End-to-end: create application -> submit -> schedule inspection -> record failed finding -> compliance action created")
    void endToEndRegistrationToCompliance() throws Exception {
        String corr = UUID.randomUUID().toString();

        // 1) Create a NEW_REGISTRATION application with siteDraft + operatorDraft
        String createBody = """
                {
                  "applicationType": "NEW_REGISTRATION",
                  "applicantName": "Applicant",
                  "applicantEmail": "applicant@example.com",
                  "siteDraft": {
                    "name": "Food Premises Alpha",
                    "type": "PREMISES",
                    "siteCode": "FP-0001",
                    "siteCategory": "FOOD_PREMISES",
                    "riskClass": "HIGH",
                    "province": "Harare",
                    "district": "Central",
                    "ward": "1"
                  },
                  "operatorDraft": {
                    "operatorType": "OPERATOR",
                    "operatorName": "Alpha Foods",
                    "contactPhone": "+263700000000",
                    "contactEmail": "ops@example.com"
                  }
                }
                """;

        MvcResult created = mockMvc.perform(post("/internal/v1/site-registry/applications")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", corr)
                        .header("X-Actor-ID", "tester")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdJson = MAPPER.readTree(created.getResponse().getContentAsString());
        JsonNode createdData = createdJson.get("data");
        assertThat(createdData).isNotNull();
        String siteId = createdData.get("site").get("siteId").asText();
        assertThat(siteId).isNotBlank();
        String applicationId = createdData.get("applications").get(0).get("applicationId").asText();
        assertThat(applicationId).isNotBlank();

        // 2) Submit application
        mockMvc.perform(post("/internal/v1/site-registry/applications/" + applicationId + "/submit")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-2")
                        .header("X-Correlation-ID", corr)
                        .header("X-Actor-ID", "tester")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        // 3) Schedule inspection (INITIAL)
        String scheduleBody = """
                {
                  "siteId": "%s",
                  "inspectionType": "INITIAL",
                  "scheduledDate": "2026-01-01",
                  "templateCode": "FOOD_PREMISES_INITIAL",
                  "inspectorRefs": ["prov:inspector-1"]
                }
                """.formatted(siteId);

        MvcResult scheduled = mockMvc.perform(post("/internal/v1/site-registry/inspections")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-3")
                        .header("X-Correlation-ID", corr)
                        .header("X-Actor-ID", "inspector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode scheduledData = MAPPER.readTree(scheduled.getResponse().getContentAsString()).get("data");
        String inspectionId = scheduledData.get("inspectionId").asText();
        assertThat(inspectionId).isNotBlank();

        // 4) Record failed finding (should create compliance action)
        String recordBody = """
                {
                  "actualDate": "2026-01-02",
                  "outcome": "FAILED",
                  "recommendation": "RECTIFY",
                  "findings": [
                    {
                      "status": "FAIL",
                      "severity": "CRITICAL",
                      "comments": "No handwashing facilities",
                      "evidenceRefs": { "photos": ["docstore:00000000-0000-0000-0000-000000000000"] },
                      "rectificationDeadline": "2026-02-01",
                      "createComplianceAction": true
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/internal/v1/site-registry/inspections/" + inspectionId + "/record")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-4")
                        .header("X-Correlation-ID", corr)
                        .header("X-Actor-ID", "inspector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody))
                .andExpect(status().isOk());

        // 5) Fetch site profile and verify compliance actions exist
        MvcResult profile = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/internal/v1/site-registry/sites/" + siteId)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-5")
                        .header("X-Correlation-ID", corr)
                        .header("X-Actor-ID", "reviewer"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode profileData = MAPPER.readTree(profile.getResponse().getContentAsString()).get("data");
        assertThat(profileData.get("complianceActions").isArray()).isTrue();
        assertThat(profileData.get("complianceActions").size()).isGreaterThanOrEqualTo(1);

        // scoring summary should be present on the inspection view
        assertThat(profileData.get("inspections").isArray()).isTrue();
        JsonNode insp0 = profileData.get("inspections").get(0);
        assertThat(insp0.get("scoreMax")).isNotNull();
    }
}

