package zw.gov.mohcc.impilo.telemonitoring.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.telemonitoring.core.ReadingIngestionService.IngestCommand;
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
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.AlertEpisodeRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * OF-B26 — the trigger/evaluation matrix (§14.6): threshold bands from the plan's
 * CURRENT threshold-profile version, the low-trust AMBER ceiling, one-live-episode
 * grouping + the live_guard race backstop, repeat-abnormal escalation (trigger 5) and
 * the per-device device-quality rate limit (trigger 9). Readings enter through the REAL
 * ingestion path so the ReadingCompletionListener seam is exercised end-to-end.
 */
@SpringBootTest
@ActiveProfiles("test")
class AlertEvaluationEngineTest {

    static final String FULL_BANDS =
            "{\"systolic_bp\":{\"unit\":\"mmHg\",\"greenMax\":140,\"amberMax\":160,\"redMax\":180},"
                    + "\"spo2\":{\"unit\":\"%\",\"greenMin\":92,\"amberMin\":88,\"redMin\":85}}";

    @Autowired private ReadingIngestionService ingestionService;
    @Autowired private DeviceAssignmentService assignmentService;
    @Autowired private MonitoringPlanService planService;
    @Autowired private MonitoringProgrammeRepository programmeRepository;
    @Autowired private AlertEpisodeRepository episodeRepository;
    @Autowired private EventOutboxRepository outboxRepository;
    @Autowired private ObjectMapper objectMapper;

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
            p.setDefaultThresholds(FULL_BANDS);
            programmeRepository.save(p);
        }
        when(iotDeviceRegistryClient.verify(any(), any(), any()))
                .thenReturn(DeviceVerification.verified("CERTIFIED"));
        when(assetRegistryClient.calibrationState(any(), any(), any()))
                .thenReturn(EquipmentCalibration.found("ACTIVE", LocalDate.now().plusMonths(6)));
        when(fhirGatewayClient.forwardResource(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FhirGatewayClient.ForwardResult(
                        FhirGatewayClient.Outcome.SUCCESS, "fhir-gateway:audit:1", "SUCCESS"));
        when(pctTaskClient.createTask(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);
        when(daidzaiIncidentClient.createIncident(any(), any(), any(), any(), any())).thenReturn("ems-1");
    }

    private MonitoringPlanEntity activePlan() {
        MonitoringPlanEntity plan = planService.createDraft(new MonitoringPlanService.CreatePlanCommand(
                tenant, "CPID-" + UUID.randomUUID(), "HYPERTENSION", null,
                "Essential hypertension", "HOME_SELF_MONITORING", "WEEKLY", null, FULL_BANDS,
                null, null, "clinician-a"), true);
        return planService.approve(plan.getId(), "clinician-b");
    }

    private String assignedDevice(MonitoringPlanEntity plan) {
        return assignedDevice(plan, null);
    }

    private String assignedDevice(MonitoringPlanEntity plan, String assetRef) {
        String device = "dev-" + UUID.randomUUID();
        var assignment = assignmentService.assign(
                new DeviceAssignmentService.AssignCommand(tenant, plan.getId(), device, assetRef,
                        "ISSUED_TO_PATIENT", "nurse-1", null), null);
        assignmentService.activate(assignment.getId(), "nurse-1", true, null);
        return device;
    }

    private void ingest(String device, String metric, String value, OffsetDateTime at) {
        ingestionService.ingest(new IngestCommand(tenant, UUID.randomUUID().toString(), device,
                metric, value, "mmHg", at, "HTTP", null));
    }

    private List<AlertEpisodeEntity> episodesFor(UUID planId) {
        return episodeRepository.findByTenantIdAndPlanIdOrderByCreatedAtDesc(tenant, planId);
    }

    private List<String> alertEvents(UUID episodeId) {
        return outboxRepository.findAll().stream()
                .filter(e -> episodeId.toString().equals(e.getAggregateId()))
                .map(e -> e.getEventType())
                .toList();
    }

    // ── Threshold band matrix (triggers 1/2) ──

    @Test
    void greenReadingRaisesNoEpisode() {
        MonitoringPlanEntity plan = activePlan();
        ingest(assignedDevice(plan), "systolic_bp", "130", OffsetDateTime.now());
        assertThat(episodesFor(plan.getId())).isEmpty();
    }

    @Test
    void amberBandBreachOpensAmberEpisode() {
        MonitoringPlanEntity plan = activePlan();
        ingest(assignedDevice(plan), "systolic_bp", "150", OffsetDateTime.now());

        List<AlertEpisodeEntity> episodes = episodesFor(plan.getId());
        assertThat(episodes).hasSize(1);
        AlertEpisodeEntity episode = episodes.get(0);
        assertThat(episode.getSeverity()).isEqualTo(AlertSeverity.AMBER);
        assertThat(episode.getTriggerClass()).isEqualTo(AlertTriggerClass.THRESHOLD_BREACH);
        assertThat(episode.getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(episode.getResponseDeadlineAt()).isAfter(OffsetDateTime.now());
        assertThat(episode.getPatientCpid()).isEqualTo(plan.getPatientCpid());
        assertThat(alertEvents(episode.getId())).contains("telemonitoring.alert.opened.v1");
    }

    @Test
    void redBandBreachOpensRedEpisode() {
        MonitoringPlanEntity plan = activePlan();
        ingest(assignedDevice(plan), "systolic_bp", "170", OffsetDateTime.now());

        assertThat(episodesFor(plan.getId()).get(0).getSeverity()).isEqualTo(AlertSeverity.RED);
    }

    @Test
    void beyondRedMaxOpensEmergencyAndAutoDispatchesDaidzai() {
        MonitoringPlanEntity plan = activePlan();
        ingest(assignedDevice(plan), "systolic_bp", "195", OffsetDateTime.now());

        AlertEpisodeEntity episode = episodesFor(plan.getId()).get(0);
        assertThat(episode.getSeverity()).isEqualTo(AlertSeverity.EMERGENCY);
        // §14.7: emergency-level episodes enter the ladder at rung 11+ — Daidzai.
        assertThat(episode.getCurrentRung()).isEqualTo(AlertEpisodeService.RUNG_DAIDZAI);
        assertThat(episode.getEmsIncidentRef()).isEqualTo("ems-1");
        assertThat(episode.getEmsDispatched()).isTrue();
        assertThat(episode.getStatus()).isEqualTo(AlertStatus.ESCALATED);
        assertThat(episode.getLadderHistory()).contains("DAIDZAI_EMS_DISPATCH");
        assertThat(alertEvents(episode.getId()))
                .contains("telemonitoring.alert.opened.v1", "telemonitoring.alert.escalated.v1");
    }

    @Test
    void lowerDirectionBandsClassifyMinBreaches() {
        MonitoringPlanEntity plan = activePlan();
        ingest(assignedDevice(plan), "spo2", "90", OffsetDateTime.now());
        AlertEpisodeEntity episode = episodesFor(plan.getId()).get(0);
        // 90 < greenMin 92, >= amberMin 88 → AMBER
        assertThat(episode.getSeverity()).isEqualTo(AlertSeverity.AMBER);
    }

    @Test
    void metricNotInProfileNeverAlerts() {
        MonitoringPlanEntity plan = activePlan();
        ingest(assignedDevice(plan), "temperature", "41.5", OffsetDateTime.now());
        assertThat(episodesFor(plan.getId())).isEmpty();
    }

    // ── One-live-episode grouping + race guard (§14.6 dedup rule) ──

    @Test
    void repeatBreachesAttachToTheLiveEpisodeNeverSpawnSiblings() {
        MonitoringPlanEntity plan = activePlan();
        String device = assignedDevice(plan);
        ingest(device, "systolic_bp", "150", OffsetDateTime.now().minusMinutes(10));
        ingest(device, "systolic_bp", "152", OffsetDateTime.now().minusMinutes(5));
        ingest(device, "systolic_bp", "151", OffsetDateTime.now());

        List<AlertEpisodeEntity> episodes = episodesFor(plan.getId());
        assertThat(episodes).hasSize(1);
        assertThat(episodes.get(0).getBreachCount()).isEqualTo(3);
        assertThat(episodes.get(0).getContributingReadings()).contains("152");
    }

    @Test
    void liveGuardUniqueConstraintBackstopsTheOpenRace() {
        String guard = "PLAN:" + UUID.randomUUID() + ":CLINICAL";
        episodeRepository.saveAndFlush(bareEpisode(guard));
        assertThatThrownBy(() -> episodeRepository.saveAndFlush(bareEpisode(guard)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── Trigger 5: repeat-abnormal escalation ──

    @Test
    void repeatAbnormalWithinWindowEscalatesSeverityAndFlipsTriggerClass() {
        MonitoringPlanEntity plan = activePlan();
        String device = assignedDevice(plan);
        ingest(device, "systolic_bp", "150", OffsetDateTime.now().minusMinutes(30));
        ingest(device, "systolic_bp", "151", OffsetDateTime.now());

        AlertEpisodeEntity episode = episodesFor(plan.getId()).get(0);
        assertThat(episode.getSeverity()).isEqualTo(AlertSeverity.RED);
        assertThat(episode.getTriggerClass()).isEqualTo(AlertTriggerClass.REPEAT_ABNORMAL);
        assertThat(episode.getInitialSeverity()).isEqualTo(AlertSeverity.AMBER);
        assertThat(alertEvents(episode.getId())).contains("telemonitoring.alert.escalated.v1");
    }

    // ── §14.6 low-trust / degraded ceiling (journey #66) ──

    @Test
    void degradedReadingIsCappedAtAmberEvenBeyondRedMax() {
        MonitoringPlanEntity plan = activePlan();
        String device = assignedDevice(plan, "ASSET-1");
        // Calibration lapses AFTER assignment → DEGRADED_VALID at ingest.
        when(assetRegistryClient.calibrationState(any(), any(), any()))
                .thenReturn(EquipmentCalibration.found("ACTIVE", LocalDate.now().minusDays(3)));

        ingest(device, "systolic_bp", "195", OffsetDateTime.now());

        AlertEpisodeEntity episode = episodesFor(plan.getId()).get(0);
        assertThat(episode.getSeverity()).isEqualTo(AlertSeverity.AMBER);
        assertThat(episode.isSeverityCeilingApplied()).isTrue();
        assertThat(episode.getEmsIncidentRef()).isNull(); // never EMS off degraded evidence alone
    }

    @Test
    void ceilingHoldsAcrossRepeatedDegradedReadingsButLiftsOnTrustedEvidence() {
        MonitoringPlanEntity plan = activePlan();
        String device = assignedDevice(plan, "ASSET-2");
        when(assetRegistryClient.calibrationState(any(), any(), any()))
                .thenReturn(EquipmentCalibration.found("ACTIVE", LocalDate.now().minusDays(3)));
        ingest(device, "systolic_bp", "195", OffsetDateTime.now().minusMinutes(20));
        ingest(device, "systolic_bp", "196", OffsetDateTime.now().minusMinutes(10));

        AlertEpisodeEntity capped = episodesFor(plan.getId()).get(0);
        assertThat(capped.getSeverity()).isEqualTo(AlertSeverity.AMBER);
        assertThat(capped.isSeverityCeilingApplied()).isTrue();

        // The verified repeat on a calibrated device (§14.6: amber prompts a verified repeat).
        when(assetRegistryClient.calibrationState(any(), any(), any()))
                .thenReturn(EquipmentCalibration.found("ACTIVE", LocalDate.now().plusMonths(6)));
        ingest(device, "systolic_bp", "197", OffsetDateTime.now());

        AlertEpisodeEntity lifted = episodeRepository.findById(capped.getId()).orElseThrow();
        assertThat(lifted.getSeverity()).isEqualTo(AlertSeverity.EMERGENCY);
        assertThat(lifted.isSeverityCeilingApplied()).isFalse();
        assertThat(lifted.getEmsDispatched()).isTrue();
    }

    // ── Trigger 9: device-quality rate limit ──

    @Test
    void persistentQuarantinedReadingsCollapseIntoOneDeviceQualityEpisode() {
        String device = "storm-" + UUID.randomUUID();
        // Never assigned → every reading quarantines (NO_ASSIGNMENT).
        for (int i = 0; i < 6; i++) {
            ingest(device, "systolic_bp", "150", OffsetDateTime.now().minusMinutes(30 - i));
        }
        List<AlertEpisodeEntity> episodes =
                episodeRepository.findByTenantIdAndDeviceRefOrderByCreatedAtDesc(tenant, device);
        assertThat(episodes).hasSize(1); // §14.6: one device-quality episode max, storms attach
        AlertEpisodeEntity episode = episodes.get(0);
        assertThat(episode.getTriggerClass()).isEqualTo(AlertTriggerClass.DEVICE_QUALITY);
        assertThat(episode.getSeverity()).isEqualTo(AlertSeverity.AMBER);
        assertThat(episode.getPatientCpid()).isNull(); // unattributed stays unattributed
        // Exactly ONE opened event — the storm did not multiply notifications.
        assertThat(alertEvents(episode.getId()))
                .containsOnlyOnce("telemonitoring.alert.opened.v1");
    }

    // ── Evaluator failure never breaks ingestion (seam contract) ──

    @Test
    void unusableBandBoundsNeverAlertAndNeverFailIngestion() {
        MonitoringPlanEntity plan = planService.createDraft(new MonitoringPlanService.CreatePlanCommand(
                tenant, "CPID-" + UUID.randomUUID(), "HYPERTENSION", null,
                "hpt", "HOME_SELF_MONITORING", "WEEKLY", null,
                "{\"systolic_bp\":{\"greenMax\":\"not-a-number\"}}",
                null, null, "clinician-a"), true);
        planService.approve(plan.getId(), "clinician-b");
        String device = assignedDevice(plan);

        ingest(device, "systolic_bp", "999", OffsetDateTime.now());

        assertThat(episodesFor(plan.getId())).isEmpty(); // no usable rule → no alert
        // and the reading itself landed (ingestion unaffected by the evaluator)
    }

    private AlertEpisodeEntity bareEpisode(String guard) {
        AlertEpisodeEntity e = new AlertEpisodeEntity();
        e.setTenantId(tenant);
        e.setTriggerClass(AlertTriggerClass.THRESHOLD_BREACH);
        e.setInitialSeverity(AlertSeverity.AMBER);
        e.setSeverity(AlertSeverity.AMBER);
        e.setStatus(AlertStatus.OPEN);
        e.setLiveGuard(guard);
        return e;
    }
}
