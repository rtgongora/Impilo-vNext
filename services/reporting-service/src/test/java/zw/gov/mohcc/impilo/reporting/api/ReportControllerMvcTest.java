package zw.gov.mohcc.impilo.reporting.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.reporting.dto.CreateReportRequest;
import zw.gov.mohcc.impilo.reporting.dto.CreateScheduleRequest;
import zw.gov.mohcc.impilo.reporting.dto.RunReportRequest;
import zw.gov.mohcc.impilo.reporting.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.reporting.persistence.repository.ReportDefinitionRepository;
import zw.gov.mohcc.impilo.reporting.persistence.repository.ReportRunRepository;
import zw.gov.mohcc.impilo.reporting.persistence.repository.ReportScheduleRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ReportControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportDefinitionRepository definitionRepository;

    @Autowired
    private ReportRunRepository runRepository;

    @Autowired
    private ReportScheduleRepository scheduleRepository;

    @Autowired
    private EventOutboxRepository outboxRepository;

    private static final String TENANT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String POD_ID = "national";
    private static final String REQUEST_ID = "req-001";
    private static final String CORRELATION_ID = "corr-001";

    @BeforeEach
    void setUp() {
        scheduleRepository.deleteAll();
        runRepository.deleteAll();
        definitionRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    // ---------------------------------------------------------------
    // POST /internal/v1/reports
    // ---------------------------------------------------------------

    @Test
    @DisplayName("POST /reports creates a report definition")
    void createReportDefinition() throws Exception {
        CreateReportRequest request = new CreateReportRequest(
                "monthly-opd", "Monthly OPD Summary",
                "Monthly outpatient department summary",
                "SELECT * FROM opd_visits", null, null);

        mockMvc.perform(post("/internal/v1/reports")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", REQUEST_ID)
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportKey").value("monthly-opd"))
                .andExpect(jsonPath("$.name").value("Monthly OPD Summary"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(definitionRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("POST /reports returns 409 for duplicate key")
    void createReportDuplicate() throws Exception {
        CreateReportRequest request = new CreateReportRequest(
                "dup-report", "Dup Report", null,
                "SELECT 1", null, null);

        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/internal/v1/reports")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", REQUEST_ID)
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/internal/v1/reports")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", "req-002")
                        .header("X-Correlation-ID", "corr-002")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // ---------------------------------------------------------------
    // POST /internal/v1/reports/{key}/run
    // ---------------------------------------------------------------

    @Test
    @DisplayName("POST /reports/{key}/run executes report and returns result")
    void runReport() throws Exception {
        createTestReport("run-test");

        mockMvc.perform(post("/internal/v1/reports/run-test/run")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", "req-run")
                        .header("X-Correlation-ID", "corr-run")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.reportKey").value("run-test"))
                .andExpect(jsonPath("$.result").isNotEmpty());

        assertThat(runRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /reports/{key}/run returns 404 for unknown key")
    void runReportNotFound() throws Exception {
        mockMvc.perform(post("/internal/v1/reports/nonexistent/run")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", "req-nf")
                        .header("X-Correlation-ID", "corr-nf")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------
    // GET /internal/v1/reports/{key}/runs
    // ---------------------------------------------------------------

    @Test
    @DisplayName("GET /reports/{key}/runs returns paginated runs")
    void listRuns() throws Exception {
        createTestReport("list-runs-test");

        // Run the report twice
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/internal/v1/reports/list-runs-test/run")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", "req-r" + i)
                            .header("X-Correlation-ID", "corr-r" + i)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/internal/v1/reports/list-runs-test/runs")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", "req-list")
                        .header("X-Correlation-ID", "corr-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items").isArray());
    }

    // ---------------------------------------------------------------
    // POST /internal/v1/reports/{key}/schedules
    // ---------------------------------------------------------------

    @Test
    @DisplayName("POST /reports/{key}/schedules creates a schedule entry")
    void createSchedule() throws Exception {
        createTestReport("sched-test");

        CreateScheduleRequest request = new CreateScheduleRequest(
                "0 6 * * *", null, null);

        mockMvc.perform(post("/internal/v1/reports/sched-test/schedules")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", "req-sched")
                        .header("X-Correlation-ID", "corr-sched")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cronExpression").value("0 6 * * *"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.reportKey").value("sched-test"));

        assertThat(scheduleRepository.count()).isEqualTo(1);
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private void createTestReport(String key) throws Exception {
        CreateReportRequest request = new CreateReportRequest(
                key, "Test " + key, null,
                "SELECT * FROM stub", null, null);

        mockMvc.perform(post("/internal/v1/reports")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", "req-setup")
                        .header("X-Correlation-ID", "corr-setup")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
