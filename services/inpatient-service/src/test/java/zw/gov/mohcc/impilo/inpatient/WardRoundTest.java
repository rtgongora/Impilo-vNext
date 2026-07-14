package zw.gov.mohcc.impilo.inpatient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.AdmissionRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WardRoundTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID ADMISSION_REF = UUID.fromString("f2000000-0000-0000-0000-000000000001");
    private static final UUID WARD_ID = UUID.fromString("11111111-1111-1111-1111-111111111101");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdmissionRepository admissionRepository;

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withTrustHeaders(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder
                .header("X-Tenant-ID", TENANT.toString())
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", "req-inpatient-trust")
                .header("X-Correlation-ID", "corr-inpatient-trust");
    }

    @BeforeEach
    void seedAdmission() {
        if (admissionRepository.findByAdmissionRef(ADMISSION_REF).isEmpty()) {
            AdmissionEntity admission = new AdmissionEntity();
            admission.setTenantId(TENANT);
            admission.setAdmissionRef(ADMISSION_REF);
            admission.setEncounterId(UUID.fromString("b1000000-0000-0000-0000-000000000001"));
            admission.setSubjectCpid("CPID-ZW-00001");
            admission.setFacilityId(UUID.fromString("f1000000-0000-0000-0000-000000000001"));
            admission.setStatus("ADMITTED");
            admission.setAdmittedAt(OffsetDateTime.now());
            admissionRepository.save(admission);
        }
    }

    @Test
    void startRound_addEntry_listByAdmission() throws Exception {
        var startResult = mockMvc.perform(withTrustHeaders(post("/internal/v1/ward-rounds"))
                        .header("Idempotency-Key", "idem-ward-round-start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "admission_ref": "%s",
                                  "ward_id": "%s",
                                  "round_type": "MORNING",
                                  "led_by": "Dr. IT Test"
                                }
                                """.formatted(ADMISSION_REF, WARD_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roundType").value("MORNING"))
                .andReturn();

        String roundId = startResult.getResponse().getContentAsString()
                .replaceAll(".*\"roundId\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(withTrustHeaders(post("/internal/v1/ward-rounds/" + roundId + "/entries"))
                        .header("Idempotency-Key", "idem-ward-round-entry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assessment": "Stable",
                                  "plan": "Continue care",
                                  "reviewedBy": "Dr. IT Test"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assessment").value("Stable"));

        mockMvc.perform(withTrustHeaders(get("/internal/v1/admissions/" + ADMISSION_REF + "/ward-rounds")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ledBy").value("Dr. IT Test"));
    }
}
