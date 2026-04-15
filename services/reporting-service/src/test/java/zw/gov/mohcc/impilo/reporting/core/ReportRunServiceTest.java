package zw.gov.mohcc.impilo.reporting.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import zw.gov.mohcc.impilo.reporting.dto.RunReportRequest;
import zw.gov.mohcc.impilo.reporting.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportDefinitionEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportRunEntity;
import zw.gov.mohcc.impilo.reporting.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.reporting.persistence.repository.ReportDefinitionRepository;
import zw.gov.mohcc.impilo.reporting.persistence.repository.ReportRunRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportRunServiceTest {

    @Mock
    private ReportRunRepository runRepository;

    @Mock
    private ReportDefinitionRepository definitionRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private ReportRunService runService;
    private ReportDefinitionService definitionService;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final String actorId = "test-actor";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        definitionService = new ReportDefinitionService(
                definitionRepository, outboxRepository, objectMapper);
        runService = new ReportRunService(
                runRepository, definitionService, outboxRepository, jdbcTemplate, objectMapper);
    }

    // ---------------------------------------------------------------
    // runReport
    // ---------------------------------------------------------------

    @Test
    @DisplayName("runReport completes successfully with JSON format")
    void runReportJsonSuccess() {
        ReportDefinitionEntity definition = buildDefinition("monthly-opd", ExportFormat.JSON);
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "monthly-opd"))
                .thenReturn(Optional.of(definition));
        when(runRepository.save(any(ReportRunEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReportRunEntity result = runService.runReport(
                tenantId, "monthly-opd", actorId, null);

        assertThat(result.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(result.getResult()).contains("monthly-opd");
        assertThat(result.getResult()).contains("rowCount");
        assertThat(result.getOutputFormat()).isEqualTo(ExportFormat.JSON);
        assertThat(result.getStartedAt()).isNotNull();
        assertThat(result.getCompletedAt()).isNotNull();
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("runReport completes successfully with CSV format")
    void runReportCsvSuccess() {
        ReportDefinitionEntity definition = buildDefinition("csv-report", ExportFormat.CSV);
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "csv-report"))
                .thenReturn(Optional.of(definition));
        when(runRepository.save(any(ReportRunEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReportRunEntity result = runService.runReport(
                tenantId, "csv-report", actorId, null);

        assertThat(result.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(result.getResult()).contains("report_key,name,generated_at,row_count");
        assertThat(result.getOutputFormat()).isEqualTo(ExportFormat.CSV);
    }

    @Test
    @DisplayName("runReport respects format override in request")
    void runReportFormatOverride() {
        ReportDefinitionEntity definition = buildDefinition("test-report", ExportFormat.JSON);
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "test-report"))
                .thenReturn(Optional.of(definition));
        when(runRepository.save(any(ReportRunEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RunReportRequest request = new RunReportRequest(null, "CSV");
        ReportRunEntity result = runService.runReport(
                tenantId, "test-report", actorId, request);

        assertThat(result.getOutputFormat()).isEqualTo(ExportFormat.CSV);
        assertThat(result.getResult()).contains("report_key,name");
    }

    @Test
    @DisplayName("runReport writes REPORT_RAN outbox event")
    void runReportWritesOutbox() {
        ReportDefinitionEntity definition = buildDefinition("test-report", ExportFormat.JSON);
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "test-report"))
                .thenReturn(Optional.of(definition));
        when(runRepository.save(any(ReportRunEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        runService.runReport(tenantId, "test-report", actorId, null);

        ArgumentCaptor<EventOutboxEntity> captor =
                ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("REPORT_RAN");
        assertThat(captor.getValue().getAggregateType()).isEqualTo("REPORT_RUN");
    }

    @Test
    @DisplayName("runReport throws when report not found")
    void runReportNotFound() {
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "nonexistent"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                runService.runReport(tenantId, "nonexistent", actorId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("runReport throws when report is inactive")
    void runReportInactive() {
        ReportDefinitionEntity definition = buildDefinition("inactive-report", ExportFormat.JSON);
        definition.setStatus(ReportStatus.INACTIVE);
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "inactive-report"))
                .thenReturn(Optional.of(definition));

        assertThatThrownBy(() ->
                runService.runReport(tenantId, "inactive-report", actorId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("runReport uses runtime parameters when provided")
    void runReportWithParameters() {
        ReportDefinitionEntity definition = buildDefinition("param-report", ExportFormat.JSON);
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "param-report"))
                .thenReturn(Optional.of(definition));
        when(runRepository.save(any(ReportRunEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RunReportRequest request = new RunReportRequest("{\"limit\":50}", null);
        ReportRunEntity result = runService.runReport(
                tenantId, "param-report", actorId, request);

        assertThat(result.getParameters()).isEqualTo("{\"limit\":50}");
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private ReportDefinitionEntity buildDefinition(String key, ExportFormat format) {
        ReportDefinitionEntity entity = new ReportDefinitionEntity();
        entity.setId(1L);
        entity.setDefinitionId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setReportKey(key);
        entity.setName("Report " + key);
        entity.setQueryTemplate("SELECT * FROM stub");
        entity.setParameters("{}");
        entity.setOutputFormat(format);
        entity.setStatus(ReportStatus.ACTIVE);
        entity.setCreatedBy("admin");
        return entity;
    }
}
