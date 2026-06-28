package zw.gov.mohcc.impilo.inpatient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InpatientClinicalDepthIT {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID ADMISSION_REF = UUID.fromString("f2000000-0000-0000-0000-000000000001");
    private static final String CPID = "CPID-ZW-00001";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdmissionRepository admissionRepository;

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withTrust(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        return b.header("X-Tenant-ID", TENANT.toString())
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", "req-clinical-depth")
                .header("X-Correlation-ID", "corr-clinical-depth")
                .header("Idempotency-Key", "idem-" + UUID.randomUUID());
    }

    @BeforeEach
    void seedAdmission() {
        if (admissionRepository.findByAdmissionRef(ADMISSION_REF).isEmpty()) {
            AdmissionEntity admission = new AdmissionEntity();
            admission.setTenantId(TENANT);
            admission.setAdmissionRef(ADMISSION_REF);
            admission.setEncounterId(UUID.fromString("b1000000-0000-0000-0000-000000000001"));
            admission.setSubjectCpid(CPID);
            admission.setFacilityId(UUID.fromString("f1000000-0000-0000-0000-000000000001"));
            admission.setStatus("ADMITTED");
            admission.setAdmittedAt(OffsetDateTime.now());
            admissionRepository.save(admission);
        }
    }

    @Test
    void carePlan_fluid_chart_handover_alert_flow() throws Exception {
        mockMvc.perform(withTrust(post("/internal/v1/care-plans"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"" + CPID + "\",\"title\":\"IT Nursing Plan\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("IT Nursing Plan"));

        mockMvc.perform(withTrust(get("/internal/v1/care-plans?patientId=" + CPID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].goals").isArray());

        mockMvc.perform(withTrust(post("/internal/v1/fluid-balance"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"" + CPID + "\",\"entryType\":\"INTAKE\",\"category\":\"IV\",\"volumeMl\":250}"))
                .andExpect(status().isCreated());

        mockMvc.perform(withTrust(post("/internal/v1/ward-charts/obs-chart/entries"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"" + CPID + "\",\"parameters\":{\"HR\":80,\"RR\":18,\"NEWS_SCORE\":2}}"))
                .andExpect(status().isCreated());

        mockMvc.perform(withTrust(post("/internal/v1/handover"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"facilityId\":\"f1000000-0000-0000-0000-000000000001\",\"outgoingStaff\":\"Sr. IT\"," +
                                "\"situation\":\"Stable ward\",\"admissionRefs\":[\"" + ADMISSION_REF + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(withTrust(post("/internal/v1/ward-alerts"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"" + CPID + "\",\"message\":\"Need assistance\",\"alertType\":\"CALL_NURSE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void clinicalWrite_referencingAnotherPatientsAdmission_isForbidden() throws Exception {
        // Subject-relationship consistency (verify-when-present): ADMISSION_REF belongs to CPID, but
        // the write claims a DIFFERENT subject — the cross-patient case must be denied (403). A
        // patientId-only write (no admissionRef) is permitted by the facility-team-level model and is
        // covered by the happy-path flow above.
        mockMvc.perform(withTrust(post("/internal/v1/care-plans"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"CPID-OTHER-9999\",\"admissionRef\":\"" + ADMISSION_REF + "\",\"title\":\"X\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(withTrust(post("/internal/v1/fluid-balance"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"CPID-OTHER-9999\",\"admissionRef\":\"" + ADMISSION_REF + "\",\"entryType\":\"INTAKE\",\"category\":\"IV\",\"volumeMl\":100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void apgar_resuscitation_mar_sync_flow() throws Exception {
        mockMvc.perform(withTrust(post("/internal/v1/apgar"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"" + CPID + "\",\"minuteMark\":1," +
                                "\"appearance\":2,\"pulse\":2,\"grimace\":1,\"activity\":2,\"respiration\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total_score").value(9));

        mockMvc.perform(withTrust(get("/internal/v1/apgar?patientId=" + CPID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].minute_mark").value(1));

        String activateBody = mockMvc.perform(withTrust(post("/internal/v1/emergency/activate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"" + CPID + "\",\"protocolType\":\"CODE_BLUE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();
        JsonNode activated = MAPPER.readTree(activateBody);
        String activationId = activated.get("id").asText();

        mockMvc.perform(withTrust(post("/internal/v1/emergency/" + activationId + "/action"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"CPR_CYCLE\",\"description\":\"Cycle 1 complete\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(withTrust(post("/internal/v1/emergency/" + activationId + "/resuscitation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cprCycles\":1,\"defibrillations\":0,\"initialRhythm\":\"Asystole\"}"))
                .andExpect(status().isCreated());

        String marSyncBody = """
                {"patientId":"%s","prescriptions":[{"prescription_id":"rx-demo-001","medication_name":"Paracetamol","dosage":"500mg","route":"PO","frequency":"Q6H"}]}
                """.formatted(CPID).trim();
        mockMvc.perform(withTrust(post("/internal/v1/mar/sync"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(marSyncBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synced").value(1));

        mockMvc.perform(withTrust(get("/internal/v1/mar?patientId=" + CPID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].drug_name").value("Paracetamol"));
    }

    @Test
    void discharge_clearance_and_resus_phase_flow() throws Exception {
        UUID encounterId = UUID.fromString("b1000000-0000-0000-0000-000000000001");

        mockMvc.perform(withTrust(post("/internal/v1/discharge-clearances/init"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"encounterId\":\"" + encounterId + "\",\"patientId\":\"" + CPID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].clearance_type").value("CLINICAL"))
                .andExpect(jsonPath("$", hasSize(9)));

        mockMvc.perform(withTrust(get("/internal/v1/discharge-clearances?encounterId=" + encounterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(9))
                .andExpect(jsonPath("$.progress.total").value(9))
                .andExpect(jsonPath("$.progress.percent").value(0));

        String clearanceList = mockMvc.perform(withTrust(get("/internal/v1/discharge-clearances?encounterId=" + encounterId)))
                .andReturn().getResponse().getContentAsString();
        String clearanceId = MAPPER.readTree(clearanceList).get("data").get(0).get("id").asText();

        mockMvc.perform(withTrust(post("/internal/v1/discharge-clearances/" + clearanceId + "/clear"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearedBy\":\"Sr. IT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLEARED"));

        String activateBody = mockMvc.perform(withTrust(post("/internal/v1/emergency/activate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"" + CPID + "\",\"protocolType\":\"CODE_BLUE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String activationId = MAPPER.readTree(activateBody).get("id").asText();

        String phaseBody = mockMvc.perform(withTrust(post("/internal/v1/emergency/" + activationId + "/phases"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phase\":\"RESUSCITATION\",\"rhythm\":\"Asystole\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phase_type").value("RESUSCITATION"))
                .andReturn().getResponse().getContentAsString();
        String phaseId = MAPPER.readTree(phaseBody).get("id").asText();

        mockMvc.perform(withTrust(post("/internal/v1/emergency/" + activationId + "/phases/" + phaseId + "/end"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"ROSC achieved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ended").value(true));

        mockMvc.perform(withTrust(get("/internal/v1/emergency/" + activationId + "/phases")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ENDED"));
    }
}
