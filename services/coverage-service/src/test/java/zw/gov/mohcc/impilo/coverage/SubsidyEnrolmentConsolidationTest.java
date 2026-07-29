package zw.gov.mohcc.impilo.coverage;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyProgramEntity;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyProgramRepository;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof of the consolidated subsidy-enrolment SoR (cv_subsidy_enrolments):
 * one enrolment row carries both the annual-cap/value concern and the exemption
 * category, and the full enrol → eligibility headroom → consume (idempotent, capped)
 * → patient-category → end → patient-category-fallback path works on that single model.
 *
 * <p>Flyway is disabled under the test profile (H2 + ddl-auto), so this proves the
 * consolidated Java surface, not the V013 migration itself — V013 is proven against
 * real Postgres separately.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubsidyEnrolmentConsolidationTest {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final String PROGRAM_CODE = "SUB-IT-CONSOLIDATED";
    private static final String CPID = "it-subsidy-consolidated-001";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubsidyProgramRepository programRepository;

    @BeforeEach
    void seedProgramme() throws Exception {
        // Flyway (V009 programme seed) does not run under the test profile — seed directly.
        // SubsidyProgramEntity is immutable (no setters); populate via reflection like
        // SubsidyEnrolmentServiceTest does.
        if (programRepository.findByTenantIdAndProgramCode(UUID.fromString(TENANT), PROGRAM_CODE).isPresent()) {
            return;
        }
        SubsidyProgramEntity program;
        var ctor = SubsidyProgramEntity.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        program = ctor.newInstance();
        setField(program, "id", UUID.randomUUID());
        setField(program, "tenantId", UUID.fromString(TENANT));
        setField(program, "programCode", PROGRAM_CODE);
        setField(program, "programName", "IT consolidated subsidy");
        setField(program, "payerId", "PAYER-MOHCC");
        setField(program, "subsidyType", "EXEMPTION");
        setField(program, "status", "ACTIVE");
        setField(program, "annualCap", new BigDecimal("100.00"));
        setField(program, "currency", "USD");
        setField(program, "effectiveFrom", LocalDate.of(2026, 1, 1));
        programRepository.save(program);
    }

    @Test
    void enrolConsumeExemptEnd_roundTripOnConsolidatedModel() throws Exception {
        // 1) Enrol with a cap override AND an exemption category on one row.
        MvcResult enrolResult = mockMvc.perform(request(post("/internal/v1/coverage/subsidies/enrolments"), "enrol-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "program_code": "%s",
                                  "member_cpid": "%s",
                                  "annual_cap_override": 50.00,
                                  "exemption_category": "indigent"
                                }
                                """.formatted(PROGRAM_CODE, CPID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.exemption_category").value("INDIGENT"))  // normalised
                .andExpect(jsonPath("$.effective_cap").value(50.00))
                .andExpect(jsonPath("$.remaining_amount").value(50.00))
                .andReturn();
        JsonNode enrolment = MAPPER.readTree(enrolResult.getResponse().getContentAsString());
        String enrolmentId = enrolment.get("id").asText();
        assertThat(enrolmentId).isNotBlank();

        // 2) Eligibility check surfaces the subsidy headroom off the consolidated row.
        mockMvc.perform(request(post("/internal/v1/eligibility/check"), "elig-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patient_cpid": "%s",
                                  "plan_code": "NO-SUCH-PLAN",
                                  "requested_amount": 20.00
                                }
                                """.formatted(CPID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsidy.available").value(true))
                .andExpect(jsonPath("$.subsidy.reason").value("SUBSIDY_HEADROOM_AVAILABLE"))
                .andExpect(jsonPath("$.subsidy.cap").value(50.00))
                .andExpect(jsonPath("$.subsidy.remaining").value(50.00));

        // 3) Consume within the cap.
        mockMvc.perform(request(post("/internal/v1/coverage/subsidies/enrolments/" + enrolmentId + "/consume"), "consume-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 20.00, "reference": "it-consol-ref-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumed_amount").value(20.00))
                .andExpect(jsonPath("$.remaining_amount").value(30.00));

        // 4) Replaying the same reference is idempotent — no double drawdown.
        mockMvc.perform(request(post("/internal/v1/coverage/subsidies/enrolments/" + enrolmentId + "/consume"), "consume-1r")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 20.00, "reference": "it-consol-ref-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumed_amount").value(20.00))
                .andExpect(jsonPath("$.remaining_amount").value(30.00));

        // 5) Breaching the cap is rejected.
        mockMvc.perform(request(post("/internal/v1/coverage/subsidies/enrolments/" + enrolmentId + "/consume"), "consume-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 40.00, "reference": "it-consol-ref-2"}
                                """))
                .andExpect(status().isBadRequest());

        // 6) Patient-category resolution reads the SAME consolidated row's exemption.
        mockMvc.perform(request(get("/internal/v1/coverage/patient-category/" + CPID), "cat-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("INDIGENT"))
                .andExpect(jsonPath("$.source").value("SUBSIDY_ENROLLMENT"));

        // 7) End the enrolment; repeat is idempotent.
        mockMvc.perform(request(post("/internal/v1/coverage/subsidies/enrolments/" + enrolmentId + "/end"), "end-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.effective_to").value(LocalDate.now().toString()));
        mockMvc.perform(request(post("/internal/v1/coverage/subsidies/enrolments/" + enrolmentId + "/end"), "end-1r"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));

        // 8) An ended enrolment no longer exempts — falls back to self-pay.
        mockMvc.perform(request(get("/internal/v1/coverage/patient-category/" + CPID), "cat-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("CASH"))
                .andExpect(jsonPath("$.source").value("DEFAULT_SELF_PAY"));

        // 9) The exemption view of the lane lists the row; value-only filter honoured.
        mockMvc.perform(request(get("/internal/v1/coverage/subsidies/enrolments"), "list-1")
                        .queryParam("member_cpid", CPID)
                        .queryParam("exemption_only", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exemption_category").value("INDIGENT"));
    }

    /** Full trust-header set; writes carry an Idempotency-Key derived from the step key. */
    private static MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder builder, String step) {
        return builder
                .header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", "req-subsidy-consol-" + step)
                .header("X-Correlation-ID", "corr-subsidy-consol-" + step)
                .header("Idempotency-Key", "idem-subsidy-consol-" + step);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
