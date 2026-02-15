package zw.gov.mohcc.impilo.reporting.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.reporting.dto.CreateReportRequest;
import zw.gov.mohcc.impilo.reporting.dto.ReportDefinitionResponse;
import zw.gov.mohcc.impilo.reporting.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportDefinitionEntity;
import zw.gov.mohcc.impilo.reporting.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.reporting.persistence.repository.ReportDefinitionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportDefinitionServiceTest {

    @Mock
    private ReportDefinitionRepository definitionRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    private ReportDefinitionService service;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final String actorId = "test-actor";

    @BeforeEach
    void setUp() {
        service = new ReportDefinitionService(
                definitionRepository, outboxRepository, new ObjectMapper());
    }

    // ---------------------------------------------------------------
    // createReport
    // ---------------------------------------------------------------

    @Test
    @DisplayName("createReport persists entity and writes outbox event")
    void createReportSuccess() {
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "monthly-opd"))
                .thenReturn(Optional.empty());
        when(definitionRepository.save(any(ReportDefinitionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateReportRequest request = new CreateReportRequest(
                "monthly-opd", "Monthly OPD Summary",
                "Monthly outpatient summary", "SELECT * FROM opd_visits",
                null, null);

        ReportDefinitionEntity result = service.createReport(tenantId, actorId, request);

        assertThat(result.getReportKey()).isEqualTo("monthly-opd");
        assertThat(result.getName()).isEqualTo("Monthly OPD Summary");
        assertThat(result.getStatus()).isEqualTo(ReportStatus.ACTIVE);
        assertThat(result.getOutputFormat()).isEqualTo(ExportFormat.JSON);
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getCreatedBy()).isEqualTo(actorId);

        ArgumentCaptor<EventOutboxEntity> outboxCaptor =
                ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("REPORT_CREATED");
        assertThat(outboxCaptor.getValue().getAggregateType()).isEqualTo("REPORT");
    }

    @Test
    @DisplayName("createReport rejects duplicate report key")
    void createReportDuplicateKey() {
        ReportDefinitionEntity existing = new ReportDefinitionEntity();
        existing.setReportKey("monthly-opd");
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "monthly-opd"))
                .thenReturn(Optional.of(existing));

        CreateReportRequest request = new CreateReportRequest(
                "monthly-opd", "Name", null,
                "SELECT 1", null, null);

        assertThatThrownBy(() -> service.createReport(tenantId, actorId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        verify(definitionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createReport uses CSV format when specified")
    void createReportCsvFormat() {
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "csv-report"))
                .thenReturn(Optional.empty());
        when(definitionRepository.save(any(ReportDefinitionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateReportRequest request = new CreateReportRequest(
                "csv-report", "CSV Report", null,
                "SELECT 1", null, "CSV");

        ReportDefinitionEntity result = service.createReport(tenantId, actorId, request);
        assertThat(result.getOutputFormat()).isEqualTo(ExportFormat.CSV);
    }

    // ---------------------------------------------------------------
    // findByKey
    // ---------------------------------------------------------------

    @Test
    @DisplayName("findByKey returns entity when found")
    void findByKeySuccess() {
        ReportDefinitionEntity entity = new ReportDefinitionEntity();
        entity.setReportKey("test-report");
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "test-report"))
                .thenReturn(Optional.of(entity));

        ReportDefinitionEntity result = service.findByKey(tenantId, "test-report");
        assertThat(result.getReportKey()).isEqualTo("test-report");
    }

    @Test
    @DisplayName("findByKey throws when not found")
    void findByKeyNotFound() {
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "nonexistent"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByKey(tenantId, "nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ---------------------------------------------------------------
    // listReports
    // ---------------------------------------------------------------

    @Test
    @DisplayName("listReports returns all reports for tenant")
    void listReportsReturnsAll() {
        ReportDefinitionEntity r1 = new ReportDefinitionEntity();
        r1.setReportKey("report-1");
        ReportDefinitionEntity r2 = new ReportDefinitionEntity();
        r2.setReportKey("report-2");
        when(definitionRepository.findByTenantId(tenantId)).thenReturn(List.of(r1, r2));

        List<ReportDefinitionEntity> result = service.listReports(tenantId);
        assertThat(result).hasSize(2);
    }

    // ---------------------------------------------------------------
    // toResponse
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toResponse maps entity fields correctly")
    void toResponseMapping() {
        ReportDefinitionEntity entity = new ReportDefinitionEntity();
        entity.setDefinitionId(UUID.randomUUID());
        entity.setReportKey("test-key");
        entity.setName("Test Report");
        entity.setDescription("A test");
        entity.setQueryTemplate("SELECT 1");
        entity.setParameters("{\"limit\": 100}");
        entity.setOutputFormat(ExportFormat.CSV);
        entity.setStatus(ReportStatus.ACTIVE);
        entity.setCreatedBy("admin");

        ReportDefinitionResponse response = service.toResponse(entity);
        assertThat(response.reportKey()).isEqualTo("test-key");
        assertThat(response.name()).isEqualTo("Test Report");
        assertThat(response.outputFormat()).isEqualTo("CSV");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    // ---------------------------------------------------------------
    // parseExportFormat
    // ---------------------------------------------------------------

    @Test
    @DisplayName("parseExportFormat defaults to JSON for null/blank/invalid")
    void parseExportFormatDefaults() {
        assertThat(ReportDefinitionService.parseExportFormat(null)).isEqualTo(ExportFormat.JSON);
        assertThat(ReportDefinitionService.parseExportFormat("")).isEqualTo(ExportFormat.JSON);
        assertThat(ReportDefinitionService.parseExportFormat("invalid")).isEqualTo(ExportFormat.JSON);
        assertThat(ReportDefinitionService.parseExportFormat("csv")).isEqualTo(ExportFormat.CSV);
        assertThat(ReportDefinitionService.parseExportFormat("JSON")).isEqualTo(ExportFormat.JSON);
    }
}
