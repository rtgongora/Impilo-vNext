package zw.gov.mohcc.impilo.scheduling;

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
class SchedulingSlotServiceIT {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void trustContext() {
        TrustContextHolder.set(new TrustContext(
                TENANT,
                "scheduler-1",
                "STAFF",
                "OPERATIONS",
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
    void reserveAndListSlots() throws Exception {
        mockMvc.perform(post("/v1/slots/reserve")
                        .header("X-Tenant-ID", TENANT.toString())
                        .header("X-Actor-ID", "scheduler-1")
                        .header("X-Actor-Type", "STAFF")
                        .header("X-Purpose-Of-Use", "OPERATIONS")
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource_id\":\"res-1\",\"date\":\"2026-06-20\",\"start_time\":\"08:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.tenant_id").value(TENANT.toString()));

        mockMvc.perform(get("/v1/slots")
                        .param("resource_id", "res-1")
                        .param("date", "2026-06-20")
                        .header("X-Tenant-ID", TENANT.toString())
                        .header("X-Actor-ID", "scheduler-1")
                        .header("X-Actor-Type", "STAFF")
                        .header("X-Purpose-Of-Use", "OPERATIONS")
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].available").value(false));
    }
}
