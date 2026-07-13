package zw.gov.mohcc.impilo.experience;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RBAC Integration Tests — validates that role-based access control
 * works correctly when JWT validation is disabled (dev mode).
 *
 * Tests verify that endpoints respond correctly for various paths
 * and that the BFF controllers don't throw startup errors.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(DockerOrExternalPostgresCondition.class)
class RbacIntegrationTest {

    private static final ExperienceBffTestRedisSupport REDIS = ExperienceBffTestRedisSupport.fromEnvironment();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        REDIS.configure(registry);
        ExperienceBffSovereignWireMockSupport.register(registry);
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    private static String requestId() { return UUID.randomUUID().toString(); }
    private static String correlationId() { return UUID.randomUUID().toString(); }

    // ── Auth endpoints should be public ──────────────────────────

    @Test
    @Order(1)
    void authLoginShouldBeAccessible() throws Exception {
        mockMvc.perform(post("/internal/v1/auth/login")
                .header("X-Tenant-ID", "tenant-moh-zw")
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", requestId())
                .header("X-Correlation-ID", correlationId())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@test.com\",\"password\":\"test\"}"))
                // With impilo.auth.fallback-enabled=true (application-test.yml), unreachable Keycloak yields 200 + dev session.
                .andExpect(status().isOk());
    }

    // ── Queue endpoints are reachable (RBAC passes) and fail clean ───
    // The BFF no longer fabricates an in-memory queue, and pct-service is not
    // running in this integration JVM, so the deterministic result is
    // 502 PCT_UNAVAILABLE. A 502 (not 401/403) proves the request passed RBAC
    // and reached the sovereign — which is what this RBAC test exists to verify.

    @Test
    @Order(2)
    void queueEntriesListPassesRbacAndFailsCleanWhenPctUnavailable() throws Exception {
        mockMvc.perform(get("/internal/v1/queue/entries")
                .param("facility_id", "a1b2c3d4-0001-4000-8000-000000000001")
                .header("X-Tenant-ID", "tenant-moh-zw")
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", requestId())
                .header("X-Correlation-ID", correlationId()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("PCT_UNAVAILABLE"));
    }

    // ── Encounter endpoints should work ──────────────────────────

    @Test
    @Order(3)
    void encounterListShouldWork() throws Exception {
        mockMvc.perform(get("/internal/v1/encounters")
                .param("patient_id", "CPID-ZW-00001")
                .header("X-Tenant-ID", "tenant-moh-zw")
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", requestId())
                .header("X-Correlation-ID", correlationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    // ── Community endpoints should work ──────────────────────────

    @Test
    @Order(4)
    void communityGroupsListShouldWork() throws Exception {
        mockMvc.perform(get("/internal/v1/community/groups")
                .header("X-Tenant-ID", "tenant-moh-zw")
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", requestId())
                .header("X-Correlation-ID", correlationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── Omnichannel endpoints should work ────────────────────────

    @Test
    @Order(5)
    void omnichannelCallbacksListShouldWork() throws Exception {
        mockMvc.perform(get("/internal/v1/omnichannel/callbacks")
                .header("X-Tenant-ID", "tenant-moh-zw")
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", requestId())
                .header("X-Correlation-ID", correlationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── Queue stats endpoint is reachable (RBAC passes) and fails clean ──

    @Test
    @Order(6)
    void queueStatsPassesRbacAndFailsCleanWhenPctUnavailable() throws Exception {
        mockMvc.perform(post("/internal/v1/queue/entries/stats")
                .header("X-Tenant-ID", "tenant-moh-zw")
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", requestId())
                .header("X-Correlation-ID", correlationId())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"facilityId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("PCT_UNAVAILABLE"));
    }

    // ── Bed endpoints should work ────────────────────────────────
    // inpatient-service is stubbed in-process (ExperienceBffSovereignWireMockSupport),
    // so this asserts the full RBAC-pass + proxy path deterministically — it no longer
    // depends on a live inpatient-service being up on localhost:8121.

    @Test
    @Order(7)
    void bedWardsListShouldWork() throws Exception {
        mockMvc.perform(get("/internal/v1/beds/wards")
                .param("facility_id", "00000000-0000-0000-0000-000000000001")
                .header("X-Tenant-ID", "tenant-moh-zw")
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", requestId())
                .header("X-Correlation-ID", correlationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── Clinical tools sync status should work ───────────────────

    @Test
    @Order(8)
    void syncStatusShouldWork() throws Exception {
        mockMvc.perform(get("/internal/v1/clinical-tools/sync/status")
                .header("X-Tenant-ID", "tenant-moh-zw")
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", requestId())
                .header("X-Correlation-ID", correlationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.syncEngine").value("available"));
    }
}
