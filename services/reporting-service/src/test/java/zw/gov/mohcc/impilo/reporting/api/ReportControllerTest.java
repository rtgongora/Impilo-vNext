package zw.gov.mohcc.impilo.reporting.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.reporting.core.*;
import zw.gov.mohcc.impilo.reporting.dto.*;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportDefinitionEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportRunEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportScheduleEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportDefinitionService definitionService;

    @Mock
    private ReportRunService runService;

    @Mock
    private ScheduleService scheduleService;

    private ReportController controller;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final String requestId = UUID.randomUUID().toString();
    private final String correlationId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        controller = new ReportController(definitionService, runService, scheduleService);
        RequestContext ctx = RequestContext.of(
                tenantId.toString(), "national", requestId, correlationId,
                null, null, null);
        RequestContextHolder.set(ctx);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    // ---------------------------------------------------------------
    // createReport
    // ---------------------------------------------------------------

    @Test
    @DisplayName("createReport returns 201 on success")
    void createReportSuccess() {
        ReportDefinitionEntity entity = buildDefinition("test-report");
        ReportDefinitionResponse response = new ReportDefinitionResponse(
                entity.getDefinitionId(), "test-report", "Test", null,
                "SELECT 1", "{}", "JSON", "ACTIVE", "admin", null, null);

        when(definitionService.createReport(eq(tenantId), anyString(), any(CreateReportRequest.class)))
                .thenReturn(entity);
        when(definitionService.toResponse(entity)).thenReturn(response);

        CreateReportRequest request = new CreateReportRequest(
                "test-report", "Test", null, "SELECT 1", null, null);

        ResponseEntity<?> result = controller.createReport(request);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("createReport returns 409 for duplicate key")
    void createReportDuplicateKey() {
        when(definitionService.createReport(eq(tenantId), anyString(), any(CreateReportRequest.class)))
                .thenThrow(new IllegalArgumentException("Report key already exists: dup"));

        CreateReportRequest request = new CreateReportRequest(
                "dup", "Dup", null, "SELECT 1", null, null);

        ResponseEntity<?> result = controller.createReport(request);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ---------------------------------------------------------------
    // runReport
    // ---------------------------------------------------------------

    @Test
    @DisplayName("runReport returns 201 on success")
    void runReportSuccess() {
        ReportRunEntity run = buildRun("test-report");
        ReportRunResponse response = new ReportRunResponse(
                run.getRunId(), "test-report", "{}", "JSON", "COMPLETED",
                "{}", null, null, null, "admin", null);

        when(runService.runReport(eq(tenantId), eq("test-report"), anyString(), any()))
                .thenReturn(run);
        when(runService.toResponse(run)).thenReturn(response);

        ResponseEntity<?> result = controller.runReport("test-report", null);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("runReport returns 404 for unknown key")
    void runReportNotFound() {
        when(runService.runReport(eq(tenantId), eq("unknown"), anyString(), any()))
                .thenThrow(new IllegalArgumentException("Report not found: unknown"));

        ResponseEntity<?> result = controller.runReport("unknown", null);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("runReport returns 409 for inactive report")
    void runReportInactive() {
        when(runService.runReport(eq(tenantId), eq("inactive"), anyString(), any()))
                .thenThrow(new IllegalStateException("Report is not active: inactive"));

        ResponseEntity<?> result = controller.runReport("inactive", null);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ---------------------------------------------------------------
    // createSchedule
    // ---------------------------------------------------------------

    @Test
    @DisplayName("createSchedule returns 201 on success")
    void createScheduleSuccess() {
        ReportDefinitionEntity definition = buildDefinition("test-report");
        ReportScheduleEntity schedule = new ReportScheduleEntity();
        schedule.setScheduleId(UUID.randomUUID());
        schedule.setDefinition(definition);
        schedule.setCronExpression("0 6 * * *");
        schedule.setParameters("{}");
        schedule.setOutputFormat(ExportFormat.JSON);
        schedule.setStatus(ScheduleStatus.ACTIVE);
        schedule.setCreatedBy("admin");

        ScheduleResponse response = new ScheduleResponse(
                schedule.getScheduleId(), "test-report", "0 6 * * *",
                "{}", "JSON", "ACTIVE", null, null, "admin", null);

        when(scheduleService.createSchedule(eq(tenantId), eq("test-report"), anyString(), any()))
                .thenReturn(schedule);
        when(scheduleService.toResponse(schedule)).thenReturn(response);

        CreateScheduleRequest request = new CreateScheduleRequest(
                "0 6 * * *", null, null);

        ResponseEntity<?> result = controller.createSchedule("test-report", request);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("createSchedule returns 404 for unknown report")
    void createScheduleNotFound() {
        when(scheduleService.createSchedule(eq(tenantId), eq("unknown"), anyString(), any()))
                .thenThrow(new IllegalArgumentException("Report not found: unknown"));

        CreateScheduleRequest request = new CreateScheduleRequest(
                "0 6 * * *", null, null);

        ResponseEntity<?> result = controller.createSchedule("unknown", request);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private ReportDefinitionEntity buildDefinition(String key) {
        ReportDefinitionEntity entity = new ReportDefinitionEntity();
        entity.setId(1L);
        entity.setDefinitionId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setReportKey(key);
        entity.setName("Report " + key);
        entity.setQueryTemplate("SELECT 1");
        entity.setParameters("{}");
        entity.setOutputFormat(ExportFormat.JSON);
        entity.setStatus(ReportStatus.ACTIVE);
        entity.setCreatedBy("admin");
        return entity;
    }

    private ReportRunEntity buildRun(String key) {
        ReportDefinitionEntity definition = buildDefinition(key);
        ReportRunEntity run = new ReportRunEntity();
        run.setId(1L);
        run.setRunId(UUID.randomUUID());
        run.setTenantId(tenantId);
        run.setDefinition(definition);
        run.setParameters("{}");
        run.setOutputFormat(ExportFormat.JSON);
        run.setStatus(RunStatus.COMPLETED);
        run.setResult("{}");
        run.setCreatedBy("admin");
        return run;
    }
}
