package zw.gov.mohcc.impilo.surv.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.surv.core.*;
import zw.gov.mohcc.impilo.surv.persistence.entity.FieldTaskEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.OutbreakEventEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicHealthOperationsControllerTest {

    @Mock private CounterService counterService;
    @Mock private IngestService ingestService;
    @Mock private OutbreakEventService outbreakEventService;
    @Mock private FieldTaskService fieldTaskService;
    @Mock private InvestigationService investigationService;
    @Mock private IntelligenceBriefService intelligenceBriefService;
    @Mock private PublicHealthOperationsHomeService operationsHomeService;
    @Mock private PublicHealthContextService contextService;
    @Mock private EnvironmentalComplaintService environmentalComplaintService;
    @Mock private PublicHealthIntelligenceService intelligenceService;
    @Mock private NompiloPublicHealthOrchestrator nompiloOrchestrator;
    @Mock private PublicHealthIntelligenceRuleEngineService ruleEngineService;

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void weeklyIdsrReturnsDerivedAggregate() {
        setContext();
        PublicHealthOperationsController controller = controller();

        zw.gov.mohcc.impilo.surv.persistence.entity.DailyCounterEntity c1 = new zw.gov.mohcc.impilo.surv.persistence.entity.DailyCounterEntity();
        c1.setSyndromeCode("CHOLERA");
        c1.setEventCount(3);
        zw.gov.mohcc.impilo.surv.persistence.entity.DailyCounterEntity c2 = new zw.gov.mohcc.impilo.surv.persistence.entity.DailyCounterEntity();
        c2.setSyndromeCode("CHOLERA");
        c2.setEventCount(2);
        when(counterService.getCounters(any(UUID.class), any(), any())).thenReturn(List.of(c1, c2));

        var response = controller.weeklyIdsr(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-07"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("report_type")).isEqualTo("WEEKLY_IDSR_AGGREGATE");
        assertThat(body.get("total_events")).isEqualTo(5L);
    }

    @Test
    void recordOutbreakCreatesLifecycleEvent() {
        setContext();
        PublicHealthOperationsController controller = controller();

        OutbreakEventEntity outbreak = new OutbreakEventEntity();
        outbreak.setId(42L);
        outbreak.setName("cholera");
        outbreak.setEventType("OUTBREAK");
        outbreak.setSeverity(Severity.CRITICAL);
        outbreak.setLifecycleStatus(OutbreakLifecycleStatus.RECORDED);
        outbreak.setSignalId(99L);
        when(outbreakEventService.recordOutbreak(any(), anyString(), any(), anyString(), any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenReturn(outbreak);

        var response = controller.recordOutbreak(
                new PublicHealthOperationsController.CreateOutbreakRequest(
                        "cholera", "cluster", "OUTBREAK", "syndrome_code", 1, 24, "CRITICAL",
                        null, null, null, null, null));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("lifecycle_status")).isEqualTo("RECORDED");
        assertThat(body.get("outbreak_id")).isEqualTo(42L);
    }

    @Test
    void recordFieldOperationAcceptsAndReturnsLifecycleEnvelope() {
        setContext();
        PublicHealthOperationsController controller = controller();
        when(ingestService.ingest(any(), anyString(), any(), anyString(), anyString(), any(), anyString()))
                .thenReturn(new IngestService.IngestResult(3, 2, 1));
        FieldTaskEntity task = new FieldTaskEntity();
        task.setId(7L);
        task.setStatus(FieldTaskStatus.PLANNED);
        when(fieldTaskService.createTask(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(task);

        var response = controller.recordFieldOperation(
                new PublicHealthOperationsController.CreateFieldOperationRequest(
                        UUID.fromString("9b2ec8af-5e6b-4016-a336-c7bc132614f5"),
                        "VISIT",
                        "COMPLETED",
                        "2026-05-14",
                        Map.of("team", "north")));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("lifecycle_status")).isEqualTo("PLANNED");
        assertThat(body.get("signals_matched")).isEqualTo(3);
        assertThat(body.get("cases_opened")).isEqualTo(1);
    }

    @Test
    void operationsHomeReturnsAggregate() {
        setContext();
        when(operationsHomeService.buildHome(any(UUID.class))).thenReturn(Map.of(
                "report_type", "OPERATIONS_HOME",
                "kpis", Map.of("active_signals", 2)));

        var response = controller().operationsHome();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("report_type")).isEqualTo("OPERATIONS_HOME");
    }

    private PublicHealthOperationsController controller() {
        return new PublicHealthOperationsController(
                counterService,
                ingestService,
                new ObjectMapper(),
                outbreakEventService,
                fieldTaskService,
                investigationService,
                intelligenceBriefService,
                operationsHomeService,
                contextService,
                environmentalComplaintService,
                intelligenceService,
                nompiloOrchestrator,
                ruleEngineService);
    }

    private void setContext() {
        RequestContextHolder.set(RequestContext.of(
                "00000000-0000-0000-0000-000000000001",
                "national",
                "req-1",
                "11111111-1111-1111-1111-111111111111",
                "token",
                () -> "tester",
                null
        ));
    }
}
