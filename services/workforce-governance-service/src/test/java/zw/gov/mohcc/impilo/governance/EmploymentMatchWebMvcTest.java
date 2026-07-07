package zw.gov.mohcc.impilo.governance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.governance.persistence.HscEmploymentEntity;
import zw.gov.mohcc.impilo.governance.persistence.HscEmploymentRepository;

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Channel B EC-number match matrix (IATG WS-D): MATCHED_BOTH,
 * MATCHED_EMPLOYMENT_ONLY, NOT_FOUND, CONFLICT (duplicate EC and
 * linked-to-someone-else), plus tenant scoping and required-field validation.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wgv_match;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmploymentMatchWebMvcTest {

    private static final String MATCH_PATH = "/internal/v1/workforce-governance/employment/match";
    private static final String UPSERT_PATH = "/v1/internal/governance/hsc/employment-records";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    HscEmploymentRepository hscEmploymentRepository;

    private HscEmploymentEntity seed(UUID tenant, String ecNumber, String linkedHealthId) {
        HscEmploymentEntity entity = new HscEmploymentEntity();
        entity.init(tenant);
        entity.setProviderWorkerId("PW-" + UUID.randomUUID().toString().substring(0, 8));
        entity.setEmploymentStatus("ACTIVE");
        entity.setEcNumber(ecNumber);
        entity.setLinkedHealthId(linkedHealthId);
        entity.setCadre("REGISTERED_GENERAL_NURSE");
        entity.setGrade("D1");
        entity.setPostTitle("Nurse");
        entity.setCurrentPostingProvince("Harare");
        entity.setCurrentPostingFacility("FAC-001");
        entity.setDisciplinaryEmploymentStatus("CLEAR");
        entity.setVerificationStatus("VERIFIED");
        return hscEmploymentRepository.save(entity);
    }

    /**
     * All four v1.1 hard-required trust headers plus Idempotency-Key
     * (enforced by V11HeaderFilter / IdempotencyFilter on /internal/v1/**).
     */
    private org.springframework.test.web.servlet.RequestBuilder matchRequest(UUID tenant, String body) {
        return post(MATCH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-ID", tenant.toString())
                .header("X-Pod-ID", "pod-test")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content(body);
    }

    private org.springframework.test.web.servlet.RequestBuilder upsertRequest(UUID tenant, String body) {
        return post(UPSERT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-ID", tenant.toString())
                .header("X-Pod-ID", "pod-test")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content(body);
    }

    /**
     * E3 fix: the HSC employment upsert endpoint can now persist {@code ecNumber}
     * (canonicalised) and {@code linkedHealthId}, so an EC-bearing employment row
     * can be created over REST and then matched to EMPLOYMENT_MATCHED — the IATG
     * e2e Step-6 precondition. Before E3 the upsert dropped {@code ecNumber} and
     * the follow-up match returned NOT_FOUND.
     */
    @Test
    void upsertPersistsCanonicalisedEcNumberEnablingMatchedBoth() throws Exception {
        UUID tenant = UUID.randomUUID();

        // lowercase + hyphen on the way in — canonicalisation must uppercase it.
        mockMvc.perform(upsertRequest(tenant, """
                        {"providerWorkerId": "PW-E3-001", "employmentStatus": "ACTIVE",
                         "linkedHealthId": "b0000000-0000-4000-8000-000000000001",
                         "ecNumber": "ec-000001", "postTitle": "Medical Officer"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ecNumber").value("EC-000001"))
                .andExpect(jsonPath("$.data.linkedHealthId").value("b0000000-0000-4000-8000-000000000001"));

        mockMvc.perform(matchRequest(tenant, """
                        {"ecNumber": "EC-000001", "healthId": "b0000000-0000-4000-8000-000000000001"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchStatus").value("MATCHED_BOTH"))
                .andExpect(jsonPath("$.data.linkedHealthId").value("b0000000-0000-4000-8000-000000000001"));
    }

    @Test
    void matchedBothWhenEcRowLinkedToSuppliedHealthId() throws Exception {
        UUID tenant = UUID.randomUUID();
        HscEmploymentEntity row = seed(tenant, "EC100001", "HID-BOTH-1");

        mockMvc.perform(matchRequest(tenant, """
                        {"ecNumber": "ec 100001", "healthId": "HID-BOTH-1"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchStatus").value("MATCHED_BOTH"))
                .andExpect(jsonPath("$.data.employmentId").value(row.getId().toString()))
                .andExpect(jsonPath("$.data.linkedHealthId").value("HID-BOTH-1"))
                .andExpect(jsonPath("$.data.cadre").value("REGISTERED_GENERAL_NURSE"))
                .andExpect(jsonPath("$.data.grade").value("D1"))
                .andExpect(jsonPath("$.data.disciplinaryEmploymentStatus").value("CLEAR"))
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.postings[0].facility").value("FAC-001"));
    }

    @Test
    void matchedEmploymentOnlyWhenRowUnlinked() throws Exception {
        UUID tenant = UUID.randomUUID();
        seed(tenant, "EC100002", null);

        mockMvc.perform(matchRequest(tenant, """
                        {"ecNumber": "EC100002", "healthId": "HID-ANY"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchStatus").value("MATCHED_EMPLOYMENT_ONLY"));
    }

    @Test
    void matchedEmploymentOnlyWhenNoHealthIdSupplied() throws Exception {
        UUID tenant = UUID.randomUUID();
        seed(tenant, "EC100003", "HID-LINKED-3");

        mockMvc.perform(matchRequest(tenant, """
                        {"ecNumber": "EC100003"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchStatus").value("MATCHED_EMPLOYMENT_ONLY"));
    }

    @Test
    void notFoundForUnknownEcNumber() throws Exception {
        UUID tenant = UUID.randomUUID();

        mockMvc.perform(matchRequest(tenant, """
                        {"ecNumber": "EC999999"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchStatus").value("NOT_FOUND"));
    }

    @Test
    void conflictForDuplicateEcNumber() throws Exception {
        UUID tenant = UUID.randomUUID();
        seed(tenant, "EC100005", "HID-A");
        seed(tenant, "EC100005", "HID-B");

        mockMvc.perform(matchRequest(tenant, """
                        {"ecNumber": "EC100005", "healthId": "HID-A"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchStatus").value("CONFLICT"))
                .andExpect(jsonPath("$.data.conflictingRecordCount").value(2))
                .andExpect(jsonPath("$.data.employmentId").doesNotExist());
    }

    @Test
    void conflictWhenRowLinkedToDifferentHealthId() throws Exception {
        UUID tenant = UUID.randomUUID();
        seed(tenant, "EC100006", "HID-OWNER");

        mockMvc.perform(matchRequest(tenant, """
                        {"ecNumber": "EC100006", "healthId": "HID-IMPOSTOR"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchStatus").value("CONFLICT"))
                .andExpect(jsonPath("$.data.employmentId").doesNotExist())
                // the other person's Health ID must never leak on a conflict
                .andExpect(content().string(not(containsString("HID-OWNER"))));
    }

    @Test
    void tenantScopedLookupNeverCrossesTenants() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seed(tenantA, "EC100007", "HID-T-A");

        mockMvc.perform(matchRequest(tenantB, """
                        {"ecNumber": "EC100007", "healthId": "HID-T-A"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchStatus").value("NOT_FOUND"));
    }

    @Test
    void missingEcNumberIsRejected() throws Exception {
        mockMvc.perform(matchRequest(UUID.randomUUID(), """
                        {"healthId": "HID-1"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidTenantHeaderIsBadRequest() throws Exception {
        mockMvc.perform(post(MATCH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-ID", "not-a-uuid")
                        .header("X-Pod-ID", "pod-test")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"ecNumber": "EC100008"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
