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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SimbaWellnessJourneyIT {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final String CPID = "it-simba-wellness-001";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withTrustHeaders(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder
                .header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", "req-simba-trust")
                .header("X-Correlation-ID", "corr-simba-trust");
    }

    @Test
    void goalsActivityDietClubChallenge_roundTrip() throws Exception {
        mockMvc.perform(withTrustHeaders(post("/internal/v1/wellness/goals"))
                        .header("Idempotency-Key", "idem-simba-goal-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "person_cpid": "%s",
                                  "goal_type": "STEPS",
                                  "target_value": 10000,
                                  "current_value": 0,
                                  "unit": "steps",
                                  "period": "DAILY",
                                  "status": "ACTIVE"
                                }
                                """.formatted(CPID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.goalType").value("STEPS"));

        mockMvc.perform(withTrustHeaders(get("/internal/v1/wellness/goals"))
                        .param("person_cpid", CPID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].personCpid").value(CPID));

        mockMvc.perform(withTrustHeaders(post("/internal/v1/wellness/activities"))
                        .header("Idempotency-Key", "idem-simba-activity-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "person_cpid": "%s",
                                  "activity_type": "WALK",
                                  "title": "Morning walk",
                                  "steps": 2500,
                                  "duration_minutes": 30
                                }
                                """.formatted(CPID)))
                .andExpect(status().isCreated());

        mockMvc.perform(withTrustHeaders(post("/internal/v1/wellness/diet"))
                        .header("Idempotency-Key", "idem-simba-diet-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "person_cpid": "%s",
                                  "meal_type": "BREAKFAST",
                                  "description": "Oats and fruit",
                                  "calories": 420
                                }
                                """.formatted(CPID)))
                .andExpect(status().isCreated());

        MvcResult clubResult = mockMvc.perform(withTrustHeaders(post("/internal/v1/wellness/clubs"))
                        .header("Idempotency-Key", "idem-simba-club-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "IT Walking Club",
                                  "club_type": "Walking",
                                  "visibility": "PUBLIC",
                                  "created_by": "IT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode club = MAPPER.readTree(clubResult.getResponse().getContentAsString());
        String clubId = club.get("clubId").asText();
        assertThat(clubId).isNotBlank();

        mockMvc.perform(withTrustHeaders(post("/internal/v1/wellness/clubs/" + clubId + "/join"))
                        .header("Idempotency-Key", "idem-simba-club-join-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "person_cpid": "%s",
                                  "role": "MEMBER"
                                }
                                """.formatted(CPID)))
                .andExpect(status().isCreated());

        MvcResult challengeResult = mockMvc.perform(withTrustHeaders(post("/internal/v1/wellness/challenges"))
                        .header("Idempotency-Key", "idem-simba-challenge-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "IT Steps Challenge",
                                  "challenge_type": "STEPS",
                                  "target_value": 5000,
                                  "unit": "steps",
                                  "start_date": "2026-06-01",
                                  "end_date": "2026-06-30",
                                  "status": "ACTIVE",
                                  "created_by": "IT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode challenge = MAPPER.readTree(challengeResult.getResponse().getContentAsString());
        String challengeId = challenge.get("challengeId").asText();

        mockMvc.perform(withTrustHeaders(post("/internal/v1/wellness/challenges/" + challengeId + "/join"))
                        .header("Idempotency-Key", "idem-simba-challenge-join-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "person_cpid": "%s"
                                }
                                """.formatted(CPID)))
                .andExpect(status().isCreated());
    }
}
