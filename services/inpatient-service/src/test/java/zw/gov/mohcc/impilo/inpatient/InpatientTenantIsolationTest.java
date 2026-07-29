package zw.gov.mohcc.impilo.inpatient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.AdmissionRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the D1 multi-tenant isolation fix: inpatient clinical data created under one tenant
 * is NOT visible to another. Before the fix every request used a hardcoded DEFAULT_TENANT, so
 * all tenants shared one bucket (a data leak). Now the tenant comes from the trust context
 * (x-tenant-id), so the same patientId under tenant A is invisible to tenant B.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InpatientTenantIsolationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdmissionRepository admissionRepository;

    private static MockHttpServletRequestBuilder withTenant(MockHttpServletRequestBuilder b, UUID tenant) {
        return b.header("X-Tenant-ID", tenant.toString())
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", "req-" + UUID.randomUUID())
                .header("X-Correlation-ID", "corr-" + UUID.randomUUID())
                .header("Idempotency-Key", "idem-" + UUID.randomUUID());
    }

    @Test
    void carePlanCreatedUnderOneTenant_isInvisibleToAnother() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        String cpid = "CPID-ISO-" + UUID.randomUUID().toString().substring(0, 8);

        // Strict subject-relationship gate: the inpatient clinical write requires the subject to have
        // an active admission in this tenant. Seed one for tenant A's patient so the create resolves —
        // the assertion under test is list-isolation across tenants, not the create itself.
        AdmissionEntity admission = new AdmissionEntity();
        admission.setTenantId(tenantA);
        admission.setAdmissionRef(UUID.randomUUID());
        admission.setEncounterId(UUID.randomUUID());
        admission.setSubjectCpid(cpid);
        admission.setFacilityId(UUID.randomUUID());
        admission.setStatus("ADMITTED");
        admission.setAdmittedAt(OffsetDateTime.now());
        admissionRepository.save(admission);

        // Create a care plan as tenant A.
        mockMvc.perform(withTenant(post("/internal/v1/care-plans"), tenantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"" + cpid + "\",\"title\":\"Tenant A plan\"}"))
                .andExpect(status().isCreated());

        // Tenant A sees its own plan.
        mockMvc.perform(withTenant(get("/internal/v1/care-plans?patientId=" + cpid), tenantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Tenant B, querying the SAME patientId, sees nothing — the leak is closed.
        mockMvc.perform(withTenant(get("/internal/v1/care-plans?patientId=" + cpid), tenantB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
