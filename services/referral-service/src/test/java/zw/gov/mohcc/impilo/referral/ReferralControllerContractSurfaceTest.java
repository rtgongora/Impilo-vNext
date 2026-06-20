package zw.gov.mohcc.impilo.referral;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReferralControllerContractSurfaceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void trustContext() {
        TrustContextHolder.set(new TrustContext(
                TENANT,
                "provider-1",
                "PROVIDER",
                "TREATMENT",
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                AccessMode.INTERNAL));
    }

    @AfterEach
    void clearTrustContext() {
        TrustContextHolder.clear();
    }

    @Test
    void healthProbe() throws Exception {
        mockMvc.perform(get("/internal/v1/health"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("ok"));
    }

    @Test
    void createListAndAcceptReferral() throws Exception {
        mockMvc.perform(post("/internal/v1/referrals")
                        .header("X-Tenant-ID", TENANT.toString())
                        .header("X-Actor-ID", "provider-1")
                        .header("X-Actor-Type", "PROVIDER")
                        .header("X-Purpose-Of-Use", "TREATMENT")
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"cpid-001\",\"reason\":\"specialist review\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.tenantId").value(TENANT.toString()));

        mockMvc.perform(get("/internal/v1/referrals")
                        .param("patientId", "cpid-001")
                        .header("X-Tenant-ID", TENANT.toString())
                        .header("X-Actor-ID", "provider-1")
                        .header("X-Actor-Type", "PROVIDER")
                        .header("X-Purpose-Of-Use", "TREATMENT")
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patientId").value("cpid-001"));
    }
}
