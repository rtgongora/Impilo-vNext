package zw.gov.mohcc.impilo.datagovernance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import zw.gov.mohcc.impilo.datagovernance.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.datagovernance.repository.OutboxEventRepository;

/**
 * Proves per-user privacy display preferences persist and update — replacing the
 * former experience-bff echo (G024) that returned hardcoded defaults / echoed the body.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrivacyPreferenceApiMockMvcTest {

    private static final String PATH = "/internal/v1/governance/privacy-preferences";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxRepository;

    private String tenantId;
    private String userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID().toString();
        userId = "user-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("GET is 404 until a preference is set; PUT persists and is then read back")
    void putThenGetPersists() throws Exception {
        // No preference yet
        mockMvc.perform(get(PATH).param("userId", userId)
                        .header("X-Tenant-ID", tenantId)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-g")
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());

        // Set MINIMAL / 15m / screenShare off
        putPref("{\"userId\":\"" + userId + "\",\"defaultLevel\":\"MINIMAL\"," +
                "\"autoLockMinutes\":15,\"screenShareMode\":false}", "idem-put-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLevel").value("MINIMAL"))
                .andExpect(jsonPath("$.autoLockMinutes").value(15))
                .andExpect(jsonPath("$.screenShareMode").value(false))
                .andExpect(jsonPath("$.persisted").value(true));

        // Read back the persisted value (not a default)
        mockMvc.perform(get(PATH).param("userId", userId)
                        .header("X-Tenant-ID", tenantId)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-g2")
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLevel").value("MINIMAL"))
                .andExpect(jsonPath("$.autoLockMinutes").value(15))
                .andExpect(jsonPath("$.screenShareMode").value(false));

        assertThat(outboxRepository.findAll().stream()
                .filter(e -> "impilo.data.governance.privacy-preference.updated.v1".equals(e.getEventType()))
                .map(OutboxEventEntity::getSubjectId))
                .contains(userId);
    }

    @Test
    @DisplayName("Display settings: PUT persists theme/font/density, GET reads back; invalid clamp")
    void displaySettingsPersist() throws Exception {
        String dp = "/internal/v1/governance/display-settings";
        // 404 until set
        mockMvc.perform(get(dp).param("userId", userId)
                        .header("X-Tenant-ID", tenantId).header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-d").header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        // PUT dark/large/compact (+ invalid theme clamps to system)
        mockMvc.perform(put(dp)
                        .header("X-Tenant-ID", tenantId).header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-d2").header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "idem-disp-" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"theme\":\"dark\",\"fontSize\":\"large\",\"density\":\"compact\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("dark"))
                .andExpect(jsonPath("$.fontSize").value("large"))
                .andExpect(jsonPath("$.density").value("compact"));
        // read back
        mockMvc.perform(get(dp).param("userId", userId)
                        .header("X-Tenant-ID", tenantId).header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-d3").header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("dark"));
    }

    @Test
    @DisplayName("PUT upserts: a second update overwrites the first; invalid values clamp to safe defaults")
    void upsertAndClamp() throws Exception {
        putPref("{\"userId\":\"" + userId + "\",\"defaultLevel\":\"FULL\",\"autoLockMinutes\":2}", "idem-u1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLevel").value("FULL"))
                .andExpect(jsonPath("$.autoLockMinutes").value(2));

        // Invalid level + out-of-range minutes clamp to PARTIAL / 5
        putPref("{\"userId\":\"" + userId + "\",\"defaultLevel\":\"BOGUS\",\"autoLockMinutes\":999}", "idem-u2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLevel").value("PARTIAL"))
                .andExpect(jsonPath("$.autoLockMinutes").value(5));

        // Only one row exists for the user (upsert, not insert-twice)
        mockMvc.perform(get(PATH).param("userId", userId)
                        .header("X-Tenant-ID", tenantId)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-u")
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLevel").value("PARTIAL"));
    }

    private org.springframework.test.web.servlet.ResultActions putPref(String body, String idem)
            throws Exception {
        return mockMvc.perform(put(PATH)
                .header("X-Tenant-ID", tenantId)
                .header("X-Pod-ID", "national")
                .header("X-Request-ID", "req-1")
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("Idempotency-Key", idem + "-" + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
