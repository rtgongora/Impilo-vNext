package zw.gov.mohcc.impilo.telemonitoring.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.telemonitoring.core.AlertEpisodeService.ContributingReading;
import zw.gov.mohcc.impilo.telemonitoring.core.AlertEpisodeService.OpenOrAttachCommand;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertSeverity;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertTriggerClass;
import zw.gov.mohcc.impilo.telemonitoring.integration.AssetRegistryClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.AssetRegistryClient.EquipmentCalibration;
import zw.gov.mohcc.impilo.telemonitoring.integration.DaidzaiIncidentClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.FhirGatewayClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.IotDeviceRegistryClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.IotDeviceRegistryClient.DeviceVerification;
import zw.gov.mohcc.impilo.telemonitoring.integration.PctTaskClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.TeleconsultReferralClient;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.AlertEpisodeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.DeviceAssignmentEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.AlertEpisodeRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.DeviceAssignmentRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * OF-B26 sweeps: device-offline detection (§14.6 trigger 8, journey #65 — three days
 * silent → episode → rung-4 CHW task, ONE episode per device) and overdue-
 * acknowledgement auto-escalation (§14.6 escalation-on-silence, audited, with the
 * low-trust ceiling held and EMERGENCY reaching the rung-11 Daidzai seam).
 */
@SpringBootTest
@ActiveProfiles("test")
class AlertSweepTest {

    @Autowired private AlertEvaluationService evaluationService;
    @Autowired private AlertEpisodeService episodeService;
    @Autowired private AlertRuleConfigService configService;
    @Autowired private MonitoringPlanService planService;
    @Autowired private DeviceAssignmentService assignmentService;
    @Autowired private MonitoringProgrammeRepository programmeRepository;
    @Autowired private DeviceAssignmentRepository assignmentRepository;
    @Autowired private AlertEpisodeRepository episodeRepository;

    @MockBean private IotDeviceRegistryClient iotDeviceRegistryClient;
    @MockBean private AssetRegistryClient assetRegistryClient;
    @MockBean private FhirGatewayClient fhirGatewayClient;
    @MockBean private PctTaskClient pctTaskClient;
    @MockBean private TeleconsultReferralClient teleconsultReferralClient;
    @MockBean private DaidzaiIncidentClient daidzaiIncidentClient;

    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void seedAndDefaultMocks() {
        if (programmeRepository.findByCode("HYPERTENSION").isEmpty()) {
            MonitoringProgrammeEntity p = new MonitoringProgrammeEntity();
            p.setCode("HYPERTENSION");
            p.setName("Hypertension");
            p.setDefaultThresholds("{\"systolic_bp\":{\"greenMax\":140}}");
            programmeRepository.save(p);
        }
        when(iotDeviceRegistryClient.verify(any(), any(), any()))
                .thenReturn(DeviceVerification.verified("CERTIFIED"));
        when(assetRegistryClient.calibrationState(any(), any(), any()))
                .thenReturn(EquipmentCalibration.found("ACTIVE", LocalDate.now().plusMonths(6)));
        when(pctTaskClient.createTask(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);
        when(daidzaiIncidentClient.createIncident(any(), any(), any(), any(), any())).thenReturn("ems-9");
    }

    private MonitoringPlanEntity activePlanWithChw() {
        MonitoringPlanEntity plan = planService.createDraft(new MonitoringPlanService.CreatePlanCommand(
                tenant, "CPID-" + UUID.randomUUID(), "HYPERTENSION", null,
                "hpt", "HOME_SELF_MONITORING", "WEEKLY", "{\"chwId\":\"chw-1\"}", null,
                null, null, "clinician-a"), true);
        return planService.approve(plan.getId(), "clinician-b");
    }

    private DeviceAssignmentEntity activeAssignment(MonitoringPlanEntity plan, String device) {
        DeviceAssignmentEntity assignment = assignmentService.assign(
                new DeviceAssignmentService.AssignCommand(tenant, plan.getId(), device, null,
                        "ISSUED_TO_PATIENT", "nurse-1", null), null);
        return assignmentService.activate(assignment.getId(), "nurse-1", true, null);
    }

    // ── Trigger 8: device offline (journey #65) ──

    @Test
    void silentDevicePastToleranceOpensOfflineEpisodeWithChwTask() {
        MonitoringPlanEntity plan = activePlanWithChw();
        String device = "silent-" + UUID.randomUUID();
        DeviceAssignmentEntity assignment = activeAssignment(plan, device);
        // Device has never sent a reading; activation was four days ago (tolerance 72h).
        assignment.setActivatedAt(OffsetDateTime.now().minusHours(96));
        assignmentRepository.save(assignment);

        int opened = evaluationService.sweepDeviceOffline(OffsetDateTime.now());

        assertThat(opened).isEqualTo(1);
        List<AlertEpisodeEntity> episodes =
                episodeRepository.findByTenantIdAndDeviceRefOrderByCreatedAtDesc(tenant, device);
        assertThat(episodes).hasSize(1);
        AlertEpisodeEntity episode = episodes.get(0);
        assertThat(episode.getTriggerClass()).isEqualTo(AlertTriggerClass.DEVICE_OFFLINE);
        assertThat(episode.getSeverity()).isEqualTo(AlertSeverity.AMBER);
        assertThat(episode.getPatientCpid()).isEqualTo(plan.getPatientCpid());
        // Journey #65: rung-4 PCT task to the assigned CHW, outcome recorded honestly.
        assertThat(episode.getCurrentRung()).isEqualTo(AlertEpisodeService.RUNG_CHW_TASK);
        assertThat(episode.getChwTaskDispatched()).isTrue();
        assertThat(episode.getLadderHistory()).contains("CHW_PCT_TASK");
    }

    @Test
    void offlineSweepIsRateLimitedToOneLiveEpisodePerDevice() {
        MonitoringPlanEntity plan = activePlanWithChw();
        String device = "silent-" + UUID.randomUUID();
        DeviceAssignmentEntity assignment = activeAssignment(plan, device);
        assignment.setActivatedAt(OffsetDateTime.now().minusHours(96));
        assignmentRepository.save(assignment);

        assertThat(evaluationService.sweepDeviceOffline(OffsetDateTime.now())).isEqualTo(1);
        assertThat(evaluationService.sweepDeviceOffline(OffsetDateTime.now())).isZero();
        assertThat(evaluationService.sweepDeviceOffline(OffsetDateTime.now())).isZero();

        assertThat(episodeRepository.findByTenantIdAndDeviceRefOrderByCreatedAtDesc(tenant, device))
                .hasSize(1);
    }

    @Test
    void deviceWithinToleranceStaysQuiet() {
        MonitoringPlanEntity plan = activePlanWithChw();
        String device = "fresh-" + UUID.randomUUID();
        activeAssignment(plan, device); // activated just now

        assertThat(evaluationService.sweepDeviceOffline(OffsetDateTime.now())).isZero();
        assertThat(episodeRepository.findByTenantIdAndDeviceRefOrderByCreatedAtDesc(tenant, device)).isEmpty();
    }

    // ── §14.6 escalation-on-silence (overdue acknowledgement) ──

    private AlertEpisodeEntity openOverdueAmber(MonitoringPlanEntity plan, boolean ceilinged) {
        AlertEpisodeEntity episode = episodeService.openOrAttach(new OpenOrAttachCommand(
                tenant, plan.getId(), plan.getPatientCpid(), "dev-o",
                AlertTriggerClass.THRESHOLD_BREACH,
                "PLAN:" + plan.getId() + ":CLINICAL:" + UUID.randomUUID(),
                AlertSeverity.AMBER, ceilinged,
                new ContributingReading(UUID.randomUUID(), "systolic_bp", "150", "VALID",
                        "UNGRADED", OffsetDateTime.now(), AlertSeverity.AMBER, ceilinged),
                configService.resolve(tenant, null, null), false));
        episode.setResponseDeadlineAt(OffsetDateTime.now().minusMinutes(5));
        return episodeRepository.save(episode);
    }

    @Test
    void unacknowledgedEpisodePastDeadlineAutoEscalatesWithAudit() {
        AlertEpisodeEntity episode = openOverdueAmber(activePlanWithChw(), false);

        int escalated = evaluationService.sweepOverdueAcknowledgements(OffsetDateTime.now());

        assertThat(escalated).isEqualTo(1);
        AlertEpisodeEntity after = episodeRepository.findById(episode.getId()).orElseThrow();
        assertThat(after.getSeverity()).isEqualTo(AlertSeverity.RED);
        assertThat(after.getAutoEscalatedAt()).isNotNull();
        assertThat(after.getResponseDeadlineAt()).isAfter(OffsetDateTime.now());
        assertThat(after.getLadderHistory()).contains("AUTO_ESCALATE_OVERDUE");
        assertThat(after.getStatus()).isEqualTo(AlertStatus.OPEN); // still unacknowledged — honest
    }

    @Test
    void acknowledgedEpisodesAreNeverSweptOverdue() {
        AlertEpisodeEntity episode = openOverdueAmber(activePlanWithChw(), false);
        episodeService.acknowledge(episode.getId(), "desk-nurse-1");

        assertThat(evaluationService.sweepOverdueAcknowledgements(OffsetDateTime.now())).isZero();
        assertThat(episodeRepository.findById(episode.getId()).orElseThrow().getSeverity())
                .isEqualTo(AlertSeverity.AMBER);
    }

    @Test
    void ceilingedEpisodeIsAuditedButNeverPushedPastAmberByTimeAlone() {
        AlertEpisodeEntity episode = openOverdueAmber(activePlanWithChw(), true);

        int first = evaluationService.sweepOverdueAcknowledgements(OffsetDateTime.now());
        assertThat(first).isEqualTo(1);
        AlertEpisodeEntity after = episodeRepository.findById(episode.getId()).orElseThrow();
        assertThat(after.getSeverity()).isEqualTo(AlertSeverity.AMBER); // §14.6 ceiling holds
        assertThat(after.getLadderHistory()).contains("OVERDUE_AUDITED");

        // Re-sweep after the reset deadline does not storm the audit trail.
        after.setResponseDeadlineAt(OffsetDateTime.now().minusMinutes(1));
        episodeRepository.save(after);
        assertThat(evaluationService.sweepOverdueAcknowledgements(OffsetDateTime.now())).isZero();
    }

    @Test
    void overdueEscalationReachingEmergencyDispatchesDaidzai() {
        AlertEpisodeEntity episode = openOverdueAmber(activePlanWithChw(), false);
        episode.setSeverity(AlertSeverity.RED);
        episodeRepository.save(episode);

        assertThat(evaluationService.sweepOverdueAcknowledgements(OffsetDateTime.now())).isEqualTo(1);

        AlertEpisodeEntity after = episodeRepository.findById(episode.getId()).orElseThrow();
        assertThat(after.getSeverity()).isEqualTo(AlertSeverity.EMERGENCY);
        assertThat(after.getEmsIncidentRef()).isEqualTo("ems-9");
        assertThat(after.getCurrentRung()).isEqualTo(AlertEpisodeService.RUNG_DAIDZAI);
        assertThat(after.getStatus()).isEqualTo(AlertStatus.ESCALATED);
    }
}
