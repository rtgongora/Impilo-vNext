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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SiteRegistryAssignmentsTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = UUID.randomUUID().toString();

    @Test
    @DisplayName("Create assignment shows up in site profile")
    void createAssignmentVisibleInProfile() throws Exception {
        String corr = UUID.randomUUID().toString();

        // Create site via application
        String createBody = """
                {
                  "applicationType": "NEW_REGISTRATION",
                  "applicantName": "Applicant",
                  "siteDraft": { "name": "Market Alpha", "type": "PREMISES", "siteCategory": "MARKET_PUBLIC", "province": "Harare" }
                }
                """;
        MvcResult created = mockMvc.perform(post("/internal/v1/site-registry/applications")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", corr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdData = MAPPER.readTree(created.getResponse().getContentAsString()).get("data");
        String siteId = createdData.get("site").get("siteId").asText();

        // Create assignment
        String assignmentBody = """
                {
                  "siteId": "%s",
                  "assignmentType": "INSPECTION",
                  "assignedRole": "INSPECTOR",
                  "assignedToRef": "prov:inspector-1",
                  "priority": "HIGH",
                  "notes": "Test assignment"
                }
                """.formatted(siteId);

        mockMvc.perform(post("/internal/v1/site-registry/assignments")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-2")
                        .header("X-Correlation-ID", corr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentBody))
                .andExpect(status().isCreated());

        // Profile contains assignments
        MvcResult profile = mockMvc.perform(get("/internal/v1/site-registry/sites/" + siteId)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-3")
                        .header("X-Correlation-ID", corr))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode profileData = MAPPER.readTree(profile.getResponse().getContentAsString()).get("data");
        assertThat(profileData.get("assignments").isArray()).isTrue();
        assertThat(profileData.get("assignments").size()).isGreaterThanOrEqualTo(1);
    }
}

