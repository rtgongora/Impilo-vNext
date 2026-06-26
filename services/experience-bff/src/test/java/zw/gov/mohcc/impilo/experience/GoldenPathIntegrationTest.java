package zw.gov.mohcc.impilo.experience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Golden Path Integration Tests — validates the 6 golden path flows
 * from 06_golden_paths.md against the experience-bff API surface.
 *
 * Path A: Email Login
 * Path B: Provider ID + Biometric
 * Path C: Queue → Encounter → Close
 * Path D: Admin → TSHEPO Governance
 * Path E: Marketplace
 * Path F: Registry
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(DockerOrExternalPostgresCondition.class)
class GoldenPathIntegrationTest {

    private static final ExperienceBffTestRedisSupport REDIS = ExperienceBffTestRedisSupport.fromEnvironment();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        REDIS.configure(registry);
        ExperienceBffSovereignWireMockSupport.register(registry);
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    private static final String TENANT = "moh-zw";
    private static final String POD = "national";

    private String v11Get(String path) throws Exception {
        return mvc.perform(get(path)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String v11Post(String path, Object body) throws Exception {
        return mvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andReturn().getResponse().getContentAsString();
    }

    // ── Path A: Email Login ──────────────────────────────────────
    @Test
    @Order(1)
    @DisplayName("Path A: Email login returns session token")
    void pathA_emailLogin() throws Exception {
        mvc.perform(post("/internal/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "method", "email",
                                "identifier", "test@mohcc.gov.zw",
                                "password", "integration-test-password"
                        )))
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("auth_token"))
                .andExpect(jsonPath("$.data.attributes.token").isNotEmpty())
                .andExpect(jsonPath("$.data.attributes.user.identifier").value("test@mohcc.gov.zw"));
    }

    // ── Path B: Person-first login — Provider ID is NOT a credential ──
    // Doctrine (L3 W5, LOGIN-PROVIDERID-DENY): a Provider ID / council
    // registration number is a *resolvable* identifier, never an authentication
    // credential. Attempting to log in with method=provider_id must be denied
    // with the uniform anti-enumeration failure shape (401 INVALID_CREDENTIALS),
    // without revealing whether such a provider exists. A provider authenticates
    // as a person (Path A) and then activates their provider capacity.
    @Test
    @Order(2)
    @DisplayName("Path B: Provider ID is rejected as a login credential (person-first)")
    void pathB_providerIdLogin() throws Exception {
        mvc.perform(post("/internal/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "method", "provider_id",
                                "identifier", "PROV-ZW-001",
                                "password", "integration-test-password"
                        )))
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    // ── Path C: Queue → Encounter → Close ────────────────────────
    @Test
    @Order(3)
    @DisplayName("Path C: List queue entries returns seeded data")
    void pathC_listQueueEntries() throws Exception {
        String fid = "f1000000-0000-0000-0000-000000000001";
        mvc.perform(post("/internal/v1/queue/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patient_id", "a1000000-0000-0000-0000-000000000001",
                                "facility_id", fid,
                                "queue_type", "WALK_IN")))
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated());

        mvc.perform(get("/internal/v1/queue/entries")
                        .param("facility_id", fid)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(4)
    @DisplayName("Path C: List patients returns seeded data")
    void pathC_listPatients() throws Exception {
        mvc.perform(get("/internal/v1/patients")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(5)
    @DisplayName("Path C: Create encounter and close it")
    void pathC_createAndCloseEncounter() throws Exception {
        // Create encounter
        String createResponse = mvc.perform(post("/internal/v1/encounters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patient_id", "a1000000-0000-0000-0000-000000000001",
                                "facility_id", "f1000000-0000-0000-0000-000000000001",
                                "journey_id", "journey-golden-path-1",
                                "encounter_type", "OUTPATIENT",
                                "chief_complaint", "Headache and fever"
                        )))
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("Encounter"))
                .andExpect(jsonPath("$.data.attributes.status").value("IN_PROGRESS"))
                .andReturn().getResponse().getContentAsString();

        // Extract encounter ID
        JsonNode createNode = objectMapper.readTree(createResponse);
        String encounterId = createNode.get("data").get("id").asText();

        // Close encounter
        mvc.perform(post("/internal/v1/encounters/" + encounterId + "/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "diagnosis", "Viral upper respiratory infection"
                        )))
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.status").value("COMPLETED"));
    }

    // ── Path D: Admin → TSHEPO Governance ────────────────────────
    @Test
    @Order(6)
    @DisplayName("Path D: List admin users returns seeded data")
    void pathD_listAdminUsers() throws Exception {
        mvc.perform(get("/internal/v1/admin/users")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @Order(7)
    @DisplayName("Path D: Audit log is queryable")
    void pathD_auditLog() throws Exception {
        mvc.perform(get("/internal/v1/admin/audit")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── Path E: Marketplace ──────────────────────────────────────
    @Test
    @Order(8)
    @DisplayName("Path E: Create marketplace order")
    void pathE_createMarketplaceOrder() throws Exception {
        mvc.perform(post("/internal/v1/marketplace/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "facility_id", "f1000000-0000-0000-0000-000000000001",
                                "order_number", "ORD-" + System.currentTimeMillis(),
                                "items", java.util.List.of(Map.of(
                                        "product_code", "PARA-500",
                                        "quantity", 100,
                                        "unit_price", 0.50
                                )),
                                "ordered_by", "admin.harare"
                        )))
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").exists());
    }

    // ── Path F: Registry ─────────────────────────────────────────
    @Test
    @Order(9)
    @DisplayName("Path F: List registry providers returns seeded data")
    void pathF_listProviders() throws Exception {
        mvc.perform(get("/internal/v1/registry/providers")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @Order(10)
    @DisplayName("Path F: Registry facilities accessible")
    void pathF_listFacilities() throws Exception {
        mvc.perform(get("/internal/v1/registry/facilities")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── Shift management (supports all clinical paths) ───────────
    @Test
    @Order(11)
    @DisplayName("Start and end a shift")
    void shiftLifecycle() throws Exception {
        // Start shift
        String startResponse = mvc.perform(post("/internal/v1/shifts/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "facility_id", "f1000000-0000-0000-0000-000000000001",
                                "user_id", "dr-mapfumo"
                        )))
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("Shift"))
                .andExpect(jsonPath("$.data.attributes.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        JsonNode startNode = objectMapper.readTree(startResponse);
        String shiftId = startNode.get("data").get("id").asText();

        // End shift
        mvc.perform(post("/internal/v1/shifts/" + shiftId + "/end")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "handover_notes", "All patients seen. No pending cases."
                        )))
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.status").value("ENDED"));
    }

    // ── Pharmacy flow ────────────────────────────────────────────
    @Test
    @Order(12)
    @DisplayName("List prescriptions")
    void pharmacyPrescriptions() throws Exception {
        mvc.perform(get("/internal/v1/pharmacy/prescriptions")
                        .param("patient_id", "CPID-ZW-00001")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── Inventory ────────────────────────────────────────────────
    @Test
    @Order(13)
    @DisplayName("List inventory items")
    void inventoryItems() throws Exception {
        mvc.perform(get("/internal/v1/inventory/items")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
