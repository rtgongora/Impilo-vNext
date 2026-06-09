package zw.gov.mohcc.impilo.coverage;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberEnrollmentIT {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void enrollmentEligibilityAndMemberCreate_roundTrip() throws Exception {
        MvcResult planResult = mockMvc.perform(post("/internal/v1/coverage")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "national-spine")
                        .header("X-Request-ID", "req-enroll-1")
                        .header("X-Correlation-ID", "corr-enroll-1")
                        .header("Idempotency-Key", "idem-enroll-plan-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planCode": "COV-MOHCC-CORE",
                                  "planName": "MOHCC National Core Scheme",
                                  "payerId": "PAYER-MOHCC",
                                  "planType": "GOVERNMENT",
                                  "effectiveFrom": "2026-01-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode plan = MAPPER.readTree(planResult.getResponse().getContentAsString());
        String planId = plan.get("id").asText();
        assertThat(planId).isNotBlank();

        mockMvc.perform(post("/internal/v1/coverage/eligibility/enrollment")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "national-spine")
                        .header("X-Request-ID", "req-enroll-2")
                        .header("X-Correlation-ID", "corr-enroll-2")
                        .header("Idempotency-Key", "idem-enroll-eligibility-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId": "it-coverage-member-001",
                                  "planId": "%s"
                                }
                                """.formatted(planId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.resultCode").value("ELIGIBLE"));

        mockMvc.perform(post("/internal/v1/coverage/members")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "national-spine")
                        .header("X-Request-ID", "req-enroll-3")
                        .header("X-Correlation-ID", "corr-enroll-3")
                        .header("Idempotency-Key", "idem-enroll-member-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId": "it-coverage-member-001",
                                  "planId": "%s",
                                  "memberNumber": "MOH-IT-0001",
                                  "relationship": "SELF",
                                  "status": "ACTIVE"
                                }
                                """.formatted(planId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberNumber").value("MOH-IT-0001"))
                .andExpect(jsonPath("$.planCode").value("COV-MOHCC-CORE"));

        mockMvc.perform(get("/internal/v1/coverage/member/it-coverage-member-001")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "national-spine")
                        .header("X-Request-ID", "req-enroll-4")
                        .header("X-Correlation-ID", "corr-enroll-4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberNumber").value("MOH-IT-0001"));
    }
}
