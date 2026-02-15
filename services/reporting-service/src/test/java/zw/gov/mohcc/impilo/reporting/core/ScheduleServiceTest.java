package zw.gov.mohcc.impilo.reporting.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.reporting.dto.CreateScheduleRequest;
import zw.gov.mohcc.impilo.reporting.dto.ScheduleResponse;
import zw.gov.mohcc.impilo.reporting.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportDefinitionEntity;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportScheduleEntity;
import zw.gov.mohcc.impilo.reporting.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.reporting.persistence.repository.ReportDefinitionRepository;
import zw.gov.mohcc.impilo.reporting.persistence.repository.ReportScheduleRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ReportScheduleRepository scheduleRepository;

    @Mock
    private ReportDefinitionRepository definitionRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    private ScheduleService scheduleService;
    private ReportDefinitionService definitionService;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final String actorId = "test-actor";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        definitionService = new ReportDefinitionService(
                definitionRepository, outboxRepository, objectMapper);
        scheduleService = new ScheduleService(
                scheduleRepository, definitionService, outboxRepository, objectMapper);
    }

    // ---------------------------------------------------------------
    // createSchedule
    // ---------------------------------------------------------------

    @Test
    @DisplayName("createSchedule persists schedule and writes outbox event")
    void createScheduleSuccess() {
        ReportDefinitionEntity definition = buildDefinition("daily-summary");
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "daily-summary"))
                .thenReturn(Optional.of(definition));
        when(scheduleRepository.save(any(ReportScheduleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateScheduleRequest request = new CreateScheduleRequest(
                "0 6 * * *", null, null);

        ReportScheduleEntity result = scheduleService.createSchedule(
                tenantId, "daily-summary", actorId, request);

        assertThat(result.getCronExpression()).isEqualTo("0 6 * * *");
        assertThat(result.getStatus()).isEqualTo(ScheduleStatus.ACTIVE);
        assertThat(result.getOutputFormat()).isEqualTo(ExportFormat.JSON);
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getCreatedBy()).isEqualTo(actorId);

        ArgumentCaptor<EventOutboxEntity> captor =
                ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("SCHEDULE_CREATED");
        assertThat(captor.getValue().getAggregateType()).isEqualTo("SCHEDULE");
    }

    @Test
    @DisplayName("createSchedule throws when report not found")
    void createScheduleNotFound() {
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "nonexistent"))
                .thenReturn(Optional.empty());

        CreateScheduleRequest request = new CreateScheduleRequest(
                "0 6 * * *", null, null);

        assertThatThrownBy(() ->
                scheduleService.createSchedule(tenantId, "nonexistent", actorId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("createSchedule uses CSV format when specified")
    void createScheduleCsvFormat() {
        ReportDefinitionEntity definition = buildDefinition("csv-report");
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "csv-report"))
                .thenReturn(Optional.of(definition));
        when(scheduleRepository.save(any(ReportScheduleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateScheduleRequest request = new CreateScheduleRequest(
                "0 0 * * MON", null, "CSV");

        ReportScheduleEntity result = scheduleService.createSchedule(
                tenantId, "csv-report", actorId, request);

        assertThat(result.getOutputFormat()).isEqualTo(ExportFormat.CSV);
    }

    @Test
    @DisplayName("createSchedule persists custom parameters")
    void createScheduleWithParameters() {
        ReportDefinitionEntity definition = buildDefinition("param-report");
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "param-report"))
                .thenReturn(Optional.of(definition));
        when(scheduleRepository.save(any(ReportScheduleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateScheduleRequest request = new CreateScheduleRequest(
                "0 0 1 * *", "{\"region\":\"north\"}", null);

        ReportScheduleEntity result = scheduleService.createSchedule(
                tenantId, "param-report", actorId, request);

        assertThat(result.getParameters()).isEqualTo("{\"region\":\"north\"}");
    }

    // ---------------------------------------------------------------
    // listSchedules
    // ---------------------------------------------------------------

    @Test
    @DisplayName("listSchedules returns schedules for report")
    void listSchedulesReturnsAll() {
        ReportDefinitionEntity definition = buildDefinition("test-report");
        when(definitionRepository.findByTenantIdAndReportKey(tenantId, "test-report"))
                .thenReturn(Optional.of(definition));

        ReportScheduleEntity s1 = new ReportScheduleEntity();
        s1.setCronExpression("0 6 * * *");
        s1.setDefinition(definition);
        ReportScheduleEntity s2 = new ReportScheduleEntity();
        s2.setCronExpression("0 0 * * MON");
        s2.setDefinition(definition);

        when(scheduleRepository.findByTenantIdAndDefinitionId(tenantId, definition.getId()))
                .thenReturn(List.of(s1, s2));

        List<ReportScheduleEntity> result = scheduleService.listSchedules(tenantId, "test-report");
        assertThat(result).hasSize(2);
    }

    // ---------------------------------------------------------------
    // toResponse
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toResponse maps schedule entity fields correctly")
    void toResponseMapping() {
        ReportDefinitionEntity definition = buildDefinition("test-key");

        ReportScheduleEntity entity = new ReportScheduleEntity();
        entity.setScheduleId(UUID.randomUUID());
        entity.setDefinition(definition);
        entity.setCronExpression("0 6 * * *");
        entity.setParameters("{}");
        entity.setOutputFormat(ExportFormat.JSON);
        entity.setStatus(ScheduleStatus.ACTIVE);
        entity.setCreatedBy("admin");

        ScheduleResponse response = scheduleService.toResponse(entity);
        assertThat(response.reportKey()).isEqualTo("test-key");
        assertThat(response.cronExpression()).isEqualTo("0 6 * * *");
        assertThat(response.status()).isEqualTo("ACTIVE");
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
        entity.setQueryTemplate("SELECT * FROM stub");
        entity.setParameters("{}");
        entity.setOutputFormat(ExportFormat.JSON);
        entity.setStatus(ReportStatus.ACTIVE);
        entity.setCreatedBy("admin");
        return entity;
    }
}
