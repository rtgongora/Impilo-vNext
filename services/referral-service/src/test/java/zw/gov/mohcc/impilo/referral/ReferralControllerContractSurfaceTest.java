package zw.gov.mohcc.impilo.referral;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReferralControllerContractSurfaceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthProbe() throws Exception {
        mockMvc.perform(get("/internal/v1/health"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("ok"));
    }

    @Test
    void createListAndAcceptReferral() throws Exception {
        mockMvc.perform(post("/internal/v1/referrals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"cpid-001\",\"reason\":\"specialist review\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/internal/v1/referrals").param("patientId", "cpid-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patientId").value("cpid-001"));
    }
}
