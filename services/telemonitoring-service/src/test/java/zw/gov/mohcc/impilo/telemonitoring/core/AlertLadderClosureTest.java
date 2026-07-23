package zw.gov.mohcc.impilo.telemonitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.telemonitoring.core.AlertEpisodeService.ContributingReading;
import zw.gov.mohcc.impilo.telemonitoring.core.AlertEpisodeService.OpenOrAttachCommand;
import zw.gov.mohcc.impilo.telemonitoring.core.AlertRuleConfigService.EffectiveAlertConfig;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertSeverity;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertTriggerClass;
import zw.gov.mohcc.impilo.telemonitoring.integration.AssetRegistryClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.DaidzaiIncidentClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.FhirGatewayClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.IotDeviceRegistryClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.PctTaskClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.TeleconsultReferralClient;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.AlertEpisodeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.AlertEpisodeRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OF-B26 — the §14.6 guarded machine, §14.7 ladder recording with honest best-effort
 * dispatch outcomes (rung 4 CHW task, rung 6/7 Vol I teleconsult front door with
 * origin=MONITORING_ALERT, rung 11 Daidzai), the accountable-closure refusal matrix,
 * the emergency-requires-assumption-of-care law (journey #61), and the §14.6 wording
 * law on event payloads (coded fields only — no free text, ever).
 */
@SpringBootTest
@ActiveProfiles("test")
class AlertLadderClosureTest {

    @Autowired private AlertEpisodeService episodeService;
    @Autowired private MonitoringPlanService planService;
    @Autowired private MonitoringProgrammeRepository programmeRepository;
    @Autowired private AlertEpisodeRepository episodeRepository;
    @Autowired private EventOutboxRepository outboxRepository;
    @Autowired private AlertRuleConfigService configService;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private IotDeviceRegistryClient iotDeviceRegistryClient;
    @MockBean private AssetRegistryClient assetRegistryClient;
    @MockBean private FhirGatewayClient fhirGatewayClient;
    @MockBean private PctTaskClient pctTaskClient;
    @MockBean private TeleconsultReferralClient teleconsultReferralClient;
    @MockBean private DaidzaiIncidentClient daidzaiIncidentClient;

    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void seed() {
        if (programmeRepository.findByCode("HYPERTENSION").isEmpty()) {
            MonitoringProgrammeEntity p = new MonitoringProgrammeEntity();
            p.setCode("HYPERTENSION");
            p.setName("Hypertension");
            p.setDefaultThresholds("{\"systolic_bp\":{\"greenMax\":140}}");
            programmeRepository.save(p);
        }
        when(pctTaskClient.createTask(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);
        when(teleconsultReferralClient.createMonitoringReviewReferral(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("case-777");
        when(daidzaiIncidentClient.createIncident(any(), any(), any(), any(), any())).thenReturn("ems-42");
    }

    private MonitoringPlanEntity activePlanWithChw() {
        MonitoringPlanEntity plan = planService.createDraft(new MonitoringPlanService.CreatePlanCommand(
                tenant, "CPID-" + UUID.randomUUID(), "HYPERTENSION", null,
                "hpt", "HOME_SELF_MONITORING", "WEEKLY", "{\"chwId\":\"chw-9\"}", null,
                null, null, "clinician-a"), true);
        return planService.approve(plan.getId(), "clinician-b");
    }

    private EffectiveAlertConfig config() {
        return configService.resolve(tenant, null, null);
    }

    private AlertEpisodeEntity openAmberEpisode(MonitoringPlanEntity plan) {
        return episodeService.openOrAttach(new OpenOrAttachCommand(
                tenant, plan.getId(), plan.getPatientCpid(), "dev-x",
                AlertTriggerClass.THRESHOLD_BREACH,
                "PLAN:" + plan.getId() + ":CLINICAL:" + UUID.randomUUID(),
                AlertSeverity.AMBER, false,
                new ContributingReading(UUID.randomUUID(), "systolic_bp", "150", "VALID",
                        "UNGRADED", OffsetDateTime.now(), AlertSeverity.AMBER, false),
                config(), false));
    }

    // ── Guarded machine ──

    @Test
    void resolveFromOpenRefusedAcknowledgementFirst() {
        AlertEpisodeEntity episode = openAmberEpisode(activePlanWithChw());
        assertThatThrownBy(() -> episodeService.resolve(episode.getId(), "dr-a", "assessed", "advised", "IMPROVED"))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("OPEN -> RESOLVED");
    }

    @Test
    void anonymousAcknowledgementRefused() {
        AlertEpisodeEntity episode = openAmberEpisode(activePlanWithChw());
        assertThatThrownBy(() -> episodeService.acknowledge(episode.getId(), " "))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("accountable actor");
    }

    @Test
    void acknowledgeThenProgressThenResolveWalksTheMachine() {
        AlertEpisodeEntity episode = openAmberEpisode(activePlanWithChw());
        episodeService.acknowledge(episode.getId(), "desk-nurse-1");
        episodeService.startProgress(episode.getId(), "desk-nurse-1");
        AlertEpisodeEntity resolved = episodeService.resolve(episode.getId(),
                "desk-nurse-1", "Reviewed contributing readings; isolated elevation", "Guided repeat + advice", "NO_ACTION_REQUIRED");

        assertThat(resolved.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(resolved.getLiveGuard()).isNull(); // guard released — a new situation may open
        assertThat(resolved.getResolvedBy()).isEqualTo("desk-nurse-1");
        assertThat(alertEventTypes(episode.getId())).contains(
                "telemonitoring.alert.opened.v1",
                "telemonitoring.alert.acknowledged.v1",
                "telemonitoring.alert.resolved.v1");
    }

    @Test
    void resolvedEpisodeRefusesFurtherLadderSteps() {
        AlertEpisodeEntity episode = openAmberEpisode(activePlanWithChw());
        episodeService.acknowledge(episode.getId(), "desk-nurse-1");
        episodeService.resolve(episode.getId(), "desk-nurse-1", "a", "b", "IMPROVED");
        assertThatThrownBy(() -> episodeService.recordLadderStep(episode.getId(), 4, "desk-nurse-1", null))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("RESOLVED");
    }

    // ── §14.6 accountable-closure refusal matrix ──

    @Test
    void closureRefusedUnlessAllFourFieldsPresent() {
        AlertEpisodeEntity episode = openAmberEpisode(activePlanWithChw());
        episodeService.acknowledge(episode.getId(), "desk-nurse-1");

        assertClosureRefused(episode.getId(), null, "assessed", "acted", "IMPROVED");
        assertClosureRefused(episode.getId(), "dr-a", null, "acted", "IMPROVED");
        assertClosureRefused(episode.getId(), "dr-a", "assessed", null, "IMPROVED");
        assertClosureRefused(episode.getId(), "dr-a", "assessed", "acted", null);
        assertClosureRefused(episode.getId(), "  ", "assessed", "acted", "IMPROVED");

        // still live — nothing above closed it
        assertThat(episodeRepository.findById(episode.getId()).orElseThrow().getStatus())
                .isEqualTo(AlertStatus.ACKNOWLEDGED);
    }

    private void assertClosureRefused(UUID id, String by, String assessment, String action, String outcome) {
        assertThatThrownBy(() -> episodeService.resolve(id, by, assessment, action, outcome))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("Closure refused");
    }

    // ── Journey #61: EMERGENCY requires assumption of care ──

    @Test
    void emergencyEpisodeCannotResolveWithoutAssumptionOfCare() {
        AlertEpisodeEntity episode = openAmberEpisode(activePlanWithChw());
        episode.setSeverity(AlertSeverity.EMERGENCY);
        episodeRepository.save(episode);
        episodeService.acknowledge(episode.getId(), "desk-nurse-1");

        assertThatThrownBy(() -> episodeService.resolve(episode.getId(), "dr-a", "assessed", "EMS dispatched", "HANDED_OVER"))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("assumption of care");

        episodeService.recordAssumptionOfCare(episode.getId(),
                "Harare Central ED assumed care 14:32", "ed-doctor-5");
        AlertEpisodeEntity resolved = episodeService.resolve(episode.getId(),
                "dr-a", "assessed", "EMS dispatched", "HANDED_OVER");
        assertThat(resolved.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(resolved.getAssumptionOfCareBy()).isEqualTo("ed-doctor-5");
    }

    // ── §14.7 ladder recording + best-effort dispatch outcomes ──

    @Test
    void rungFourDispatchesChwTaskAndRecordsHonestOutcome() {
        MonitoringPlanEntity plan = activePlanWithChw();
        AlertEpisodeEntity episode = openAmberEpisode(plan);
        episodeService.acknowledge(episode.getId(), "desk-nurse-1");

        AlertEpisodeEntity after = episodeService.recordLadderStep(episode.getId(), 4, "desk-nurse-1", null);

        assertThat(after.getStatus()).isEqualTo(AlertStatus.ESCALATED);
        assertThat(after.getCurrentRung()).isEqualTo(4);
        assertThat(after.getChwTaskDispatched()).isTrue();
        assertThat(after.getLadderHistory()).contains("CHW_PCT_TASK").contains("TASK_DISPATCHED");
        verify(pctTaskClient).createTask(eq(tenant), eq("MONITORING_ALERT_FOLLOWUP"),
                eq("chw-9"), eq("CHW"), any(), any(), any());
    }

    @Test
    void rungFourWithDegradedPctRecordsFailureHonestly() {
        when(pctTaskClient.createTask(any(), any(), any(), any(), any(), any(), any())).thenReturn(false);
        AlertEpisodeEntity episode = openAmberEpisode(activePlanWithChw());
        episodeService.acknowledge(episode.getId(), "desk-nurse-1");

        AlertEpisodeEntity after = episodeService.recordLadderStep(episode.getId(), 4, "desk-nurse-1", null);

        assertThat(after.getChwTaskDispatched()).isFalse();
        assertThat(after.getLadderHistory()).contains("TASK_DISPATCH_FAILED_EVENT_DURABLE");
        // The escalated outbox event is still the durable seam.
        assertThat(alertEventTypes(episode.getId())).contains("telemonitoring.alert.escalated.v1");
    }

    @Test
    void rungSevenCreatesTeleconsultCaseThroughVolOneFrontDoor() {
        MonitoringPlanEntity plan = activePlanWithChw();
        AlertEpisodeEntity episode = openAmberEpisode(plan);
        episodeService.acknowledge(episode.getId(), "desk-nurse-1");

        AlertEpisodeEntity after = episodeService.recordLadderStep(episode.getId(), 7, "desk-nurse-1", null);

        assertThat(after.getReviewReferralRef()).isEqualTo("case-777");
        assertThat(after.getLadderHistory()).contains("URGENT_TELECONSULT").contains("CASE_CREATED:case-777");
        verify(teleconsultReferralClient).createMonitoringReviewReferral(
                eq(tenant), eq(episode.getId()), eq(plan.getPatientCpid()),
                eq("THRESHOLD_BREACH"), eq("systolic_bp"), eq(true));
    }

    @Test
    void rungSevenFailedCaseCreationIsRecordedNotHidden() {
        when(teleconsultReferralClient.createMonitoringReviewReferral(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(null);
        AlertEpisodeEntity episode = openAmberEpisode(activePlanWithChw());
        episodeService.acknowledge(episode.getId(), "desk-nurse-1");

        AlertEpisodeEntity after = episodeService.recordLadderStep(episode.getId(), 7, "desk-nurse-1", null);

        assertThat(after.getReviewReferralRef()).isNull();
        assertThat(after.getLadderHistory()).contains("CASE_CREATE_FAILED_EVENT_DURABLE");
        assertThat(after.getStatus()).isEqualTo(AlertStatus.ESCALATED); // the record never lies
    }

    @Test
    void rungElevenRaisesDaidzaiIncidentWithHonestRef() {
        AlertEpisodeEntity episode = openAmberEpisode(activePlanWithChw());
        episodeService.acknowledge(episode.getId(), "desk-nurse-1");

        AlertEpisodeEntity after = episodeService.recordLadderStep(episode.getId(), 11, "desk-nurse-1", null);

        assertThat(after.getEmsIncidentRef()).isEqualTo("ems-42");
        assertThat(after.getEmsDispatched()).isTrue();
        assertThat(after.getLadderHistory()).contains("DAIDZAI_EMS_DISPATCH").contains("INCIDENT_CREATED:ems-42");
    }

    @Test
    void invalidRungRefused() {
        AlertEpisodeEntity episode = openAmberEpisode(activePlanWithChw());
        assertThatThrownBy(() -> episodeService.recordLadderStep(episode.getId(), 0, "a", null))
                .isInstanceOf(TelemonitoringDomainException.class);
        assertThatThrownBy(() -> episodeService.recordLadderStep(episode.getId(), 16, "a", null))
                .isInstanceOf(TelemonitoringDomainException.class);
    }

    // ── §14.6 wording law: events carry coded fields only ──

    @Test
    void alertEventPayloadsNeverCarryFreeTextOrClinicalWording() throws Exception {
        AlertEpisodeEntity episode = openAmberEpisode(activePlanWithChw());
        episodeService.acknowledge(episode.getId(), "desk-nurse-1");
        episodeService.recordLadderStep(episode.getId(), 7, "desk-nurse-1",
                "patient reports severe headache and blurred vision"); // free-text note
        episodeService.resolve(episode.getId(), "dr-a",
                "Hypertensive urgency assessed by teleconsult", "Medication titrated", "IMPROVED");

        List<EventOutboxEntity> events = outboxRepository.findAll().stream()
                .filter(e -> episode.getId().toString().equals(e.getAggregateId()))
                .toList();
        assertThat(events).isNotEmpty();
        for (EventOutboxEntity event : events) {
            String payload = event.getPayloadJson();
            // Free-text note and clinical assessment text must NEVER ride an event.
            assertThat(payload).doesNotContain("headache").doesNotContain("blurred vision");
            assertThat(payload).doesNotContain("Hypertensive urgency");
            JsonNode node = objectMapper.readTree(payload);
            assertThat(node.has("assessment")).isFalse();
            assertThat(node.has("note")).isFalse();
            // Coded trigger + metric only (§14.6 wording law).
            assertThat(node.get("triggerClass").asText()).isEqualTo("THRESHOLD_BREACH");
            assertThat(node.get("metricCode").asText()).isEqualTo("systolic_bp");
        }
        // The resolved event carries the coded outcome classification, nothing narrative.
        EventOutboxEntity resolvedEvent = events.stream()
                .filter(e -> e.getEventType().equals("telemonitoring.alert.resolved.v1"))
                .findFirst().orElseThrow();
        assertThat(objectMapper.readTree(resolvedEvent.getPayloadJson()).get("outcome").asText())
                .isEqualTo("IMPROVED");
    }

    private List<String> alertEventTypes(UUID episodeId) {
        return outboxRepository.findAll().stream()
                .filter(e -> episodeId.toString().equals(e.getAggregateId()))
                .map(EventOutboxEntity::getEventType)
                .toList();
    }
}
