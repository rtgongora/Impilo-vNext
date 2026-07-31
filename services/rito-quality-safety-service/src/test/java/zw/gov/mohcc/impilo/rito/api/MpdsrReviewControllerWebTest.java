package zw.gov.mohcc.impilo.rito.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MpdsrReviewControllerWebTest {

    @Autowired private MockMvc mockMvc;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder staff(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, UUID tenant) {
        return b.header("X-Tenant-ID", tenant.toString())
                .header("X-Pod-ID", "pod-1")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("X-Actor-ID", "qi-officer-1")
                .header("X-Actor-Type", "OPERATOR");
    }

    @Test
    void intakeAndDetail_smoke() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID deathCase = UUID.randomUUID();
        UUID facility = UUID.randomUUID();

        String body = MAPPER.writeValueAsString(java.util.Map.of(
                "deathCaseId", deathCase.toString(),
                "facilityId", facility.toString(),
                "flags", "MATERNAL"));

        String created = mockMvc.perform(staff(post("/internal/v1/mpdsr/reviews/from-death-event"), tenant)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deathCaseId").value(deathCase.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();

        String reviewId = MAPPER.readTree(created).get("id").asText();

        mockMvc.perform(staff(get("/internal/v1/mpdsr/reviews/" + reviewId), tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.id").value(reviewId))
                .andExpect(jsonPath("$.findings").isArray());

        // Idempotent second intake
        mockMvc.perform(staff(post("/internal/v1/mpdsr/reviews/from-death-event"), tenant)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(reviewId));
    }
}
