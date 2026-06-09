package zw.gov.mohcc.impilo.pct;

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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EdVisitIT {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final UUID FACILITY = UUID.fromString("f1000000-0000-0000-0000-000000000001");
    private static final String CPID = "CPID-ED-FULL-001";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withTrust(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        return b.header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", "req-ed")
                .header("X-Correlation-ID", "corr-ed")
                .header("X-Actor-ID", "ed-nurse-001")
                .header("X-Actor-Type", "PROVIDER")
                .header("X-Purpose-Of-Use", "TREATMENT")
                .header("X-Facility-ID", FACILITY.toString());
    }

    @Test
    void full_ed_visit_journey() throws Exception {
        String createBody = mockMvc.perform(withTrust(post("/v1/ed/visits"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "facilityId": "%s",
                                  "patientCpid": "%s",
                                  "arrivalMode": "AMBULANCE",
                                  "chiefComplaint": "Chest pain and shortness of breath"
                                }
                                """.formatted(FACILITY, CPID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.patient_cpid").value(CPID))
                .andReturn().getResponse().getContentAsString();

        String visitId = MAPPER.readTree(createBody).path("data").path("visit_id").asText();

        mockMvc.perform(withTrust(post("/v1/ed/visits/" + visitId + "/triage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "acuity": 2,
                                  "painScore": 8,
                                  "news2Score": 7,
                                  "dangerSigns": ["Breathing difficulty"],
                                  "vitals": {"hr": 110, "sbp": 90, "spo2": 92}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TRIAGED"))
                .andExpect(jsonPath("$.data.zone").value("RESUS"));

        mockMvc.perform(withTrust(post("/v1/ed/visits/" + visitId + "/encounter"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_TREATMENT"));

        mockMvc.perform(withTrust(post("/v1/ed/visits/" + visitId + "/trauma/activate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"traumaLevel\": 2, \"mechanism\": \"RTA Driver\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active_trauma_id").exists());

        mockMvc.perform(withTrust(post("/v1/ed/visits/" + visitId + "/trauma/survey"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "surveyType": "PRIMARY",
                                  "sectionKey": "A",
                                  "checklist": {"Airway patent": true}
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(withTrust(get("/v1/ed/visits/" + visitId + "/protocol-suggestions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(5)));

        mockMvc.perform(withTrust(post("/v1/ed/visits/" + visitId + "/page"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageType\": \"CODE_BLUE\", \"message\": \"Resus bay 2\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(withTrust(post("/v1/ed/visits/" + visitId + "/disposition"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dispositionType": "ADMIT",
                                  "primaryDiagnosisCode": "I21.9",
                                  "primaryDiagnosisDisplay": "Acute myocardial infarction",
                                  "destinationWard": "CCU"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.disposition.disposition_type").value("ADMIT"));
    }
}
