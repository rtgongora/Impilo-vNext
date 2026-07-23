package zw.gov.mohcc.impilo.telemonitoring.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.telemonitoring.core.ReadingIngestionService.IngestCommand;
import zw.gov.mohcc.impilo.telemonitoring.core.ReadingIngestionService.IngestOutcome;
import zw.gov.mohcc.impilo.telemonitoring.domain.ReadingStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.ShrWriteState;
import zw.gov.mohcc.impilo.telemonitoring.events.TelemetryReadingConsumer;
import zw.gov.mohcc.impilo.telemonitoring.integration.AssetRegistryClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.AssetRegistryClient.EquipmentCalibration;
import zw.gov.mohcc.impilo.telemonitoring.integration.FhirGatewayClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.IotDeviceRegistryClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.IotDeviceRegistryClient.DeviceVerification;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.DeviceAssignmentEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.TelemetryReadingEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.TelemetryReadingRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OF-B25 — the assignment gate (journey #62), ingest dedup (journey #64 first half) and
 * consumer malformed-payload tolerance. The fhir-gateway seam is mocked at the client
 * boundary; the gate, plan engine and dedup constraint run for real on H2.
 *
 * <p>Journey #62's failure criterion is a wrong-record write: every quarantine leg
 * asserts the stored row carries NO patient attribution and that the gateway was never
 * called for it.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ReadingIngestionGateTest {

    @Autowired private ReadingIngestionService ingestionService;
    @Autowired private DeviceAssignmentService assignmentService;
    @Autowired private MonitoringPlanService planService;
    @Autowired private MonitoringProgrammeRepository programmeRepository;
    @Autowired private TelemetryReadingRepository readingRepository;
    @Autowired private EventOutboxRepository outboxRepository;

    @MockBean private IotDeviceRegistryClient iotDeviceRegistryClient;
    @MockBean private AssetRegistryClient assetRegistryClient;
    @MockBean private FhirGatewayClient fhirGatewayClient;

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
        when(fhirGatewayClient.forwardResource(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FhirGatewayClient.ForwardResult(
                        FhirGatewayClient.Outcome.SUCCESS, "fhir-gateway:audit:1", "SUCCESS"));
    }

    private MonitoringPlanEntity activePlan() {
        MonitoringPlanEntity plan = planService.createDraft(new MonitoringPlanService.CreatePlanCommand(
                tenant, "CPID-" + UUID.randomUUID(), "HYPERTENSION", null,
                "Essential hypertension", "HOME_SELF_MONITORING", "WEEKLY", null, null,
                null, null, "clinician-a"), true);
        return planService.approve(plan.getId(), "clinician-b");
    }

    private DeviceAssignmentEntity activeAssignment(MonitoringPlanEntity plan, String device) {
        DeviceAssignmentEntity assignment = assignmentService.assign(
                new DeviceAssignmentService.AssignCommand(tenant, plan.getId(), device, null,
                        "ISSUED_TO_PATIENT", "nurse-1", null), null);
        return assignmentService.activate(assignment.getId(), "nurse-1", true, null);
    }

    private IngestCommand reading(String device, String metric, String value, OffsetDateTime at, String claimed) {
        return new IngestCommand(tenant, UUID.randomUUID().toString(), device, metric,
                value, "mmHg", at, "HTTP", claimed);
    }

    private List<String> readingEvents(UUID readingId) {
        return outboxRepository.findAll().stream()
                .filter(e -> readingId.toString().equals(e.getAggregateId()))
                .map(zw.gov.mohcc.impilo.telemonitoring.persistence.entity.EventOutboxEntity::getEventType)
                .toList();
    }

    // ── Journey #62: the assignment gate — quarantine legs ──

    @Test
    void unassignedDeviceQuarantinesWithNoPatientAttribution() {
        IngestOutcome outcome = ingestionService.ingest(
                reading("never-assigned-" + UUID.randomUUID(), "systolic_bp", "150", OffsetDateTime.now(), null));

        TelemetryReadingEntity row = outcome.reading();
        assertThat(outcome.deduplicated()).isFalse();
        assertThat(row.getStatus()).isEqualTo(ReadingStatus.QUARANTINED);
        assertThat(row.getQuarantineReason()).isEqualTo(ReadingIngestionService.REASON_NO_ASSIGNMENT);
        // The failure criterion: a wrong-record write. No attribution may exist to write under.
        assertThat(row.getPatientCpid()).isNull();
        assertThat(row.getPlanId()).isNull();
        assertThat(row.getAssignmentId()).isNull();
        assertThat(row.getShrWriteState()).isEqualTo(ShrWriteState.NOT_ELIGIBLE);
        assertThat(readingEvents(row.getId())).containsExactly("telemonitoring.reading.quarantined.v1");
        verify(fhirGatewayClient, never()).forwardResource(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void suspendedAssignmentQuarantinesWithNoPatientAttribution() {
        MonitoringPlanEntity plan = activePlan();
        String device = UUID.randomUUID().toString();
        DeviceAssignmentEntity assignment = activeAssignment(plan, device);
        assignmentService.suspend(assignment.getId(), "Device fault reported", "nurse-1");

        TelemetryReadingEntity row = ingestionService.ingest(
                reading(device, "systolic_bp", "150", OffsetDateTime.now(), null)).reading();

        assertThat(row.getStatus()).isEqualTo(ReadingStatus.QUARANTINED);
        assertThat(row.getQuarantineReason()).isEqualTo(ReadingIngestionService.REASON_SUSPENDED_ASSIGNMENT);
        assertThat(row.getPatientCpid()).isNull();
        assertThat(row.getPlanId()).isNull();
        assertThat(readingEvents(row.getId())).containsExactly("telemonitoring.reading.quarantined.v1");
        verify(fhirGatewayClient, never()).forwardResource(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void payloadSubjectMismatchQuarantinesWithNoPatientAttribution() {
        MonitoringPlanEntity plan = activePlan();
        String device = UUID.randomUUID().toString();
        activeAssignment(plan, device);

        TelemetryReadingEntity row = ingestionService.ingest(
                reading(device, "systolic_bp", "150", OffsetDateTime.now(), "CPID-SOMEONE-ELSE")).reading();

        assertThat(row.getStatus()).isEqualTo(ReadingStatus.QUARANTINED);
        assertThat(row.getQuarantineReason()).isEqualTo(ReadingIngestionService.REASON_SUBJECT_MISMATCH);
        // Neither the claimed subject nor the assignment's patient may be attributed.
        assertThat(row.getPatientCpid()).isNull();
        assertThat(row.getPlanId()).isNull();
        assertThat(row.getQualityStamp()).contains("CPID-SOMEONE-ELSE");
        assertThat(readingEvents(row.getId())).containsExactly("telemonitoring.reading.quarantined.v1");
        verify(fhirGatewayClient, never()).forwardResource(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void matchingPayloadSubjectClaimPassesTheGate() {
        MonitoringPlanEntity plan = activePlan();
        String device = UUID.randomUUID().toString();
        activeAssignment(plan, device);

        TelemetryReadingEntity row = ingestionService.ingest(
                reading(device, "systolic_bp", "150", OffsetDateTime.now(), plan.getPatientCpid())).reading();

        assertThat(row.getStatus()).isEqualTo(ReadingStatus.VALID);
        assertThat(row.getPatientCpid()).isEqualTo(plan.getPatientCpid());
    }

    @Test
    void unparseableValueQuarantinedNeverDropped() {
        MonitoringPlanEntity plan = activePlan();
        String device = UUID.randomUUID().toString();
        activeAssignment(plan, device);

        TelemetryReadingEntity row = ingestionService.ingest(
                reading(device, "systolic_bp", "not-a-number", OffsetDateTime.now(), null)).reading();

        assertThat(row.getStatus()).isEqualTo(ReadingStatus.QUARANTINED);
        assertThat(row.getQuarantineReason()).isEqualTo(ReadingIngestionService.REASON_UNPARSEABLE_VALUE);
        assertThat(row.getMetricValue()).isNull();
        assertThat(row.getQualityStamp()).contains("not-a-number");
        verify(fhirGatewayClient, never()).forwardResource(any(), any(), any(), any(), any(), any(), any());
    }

    // ── §15.5/§15.7: degraded calibration is STAMPED, never dropped ──

    @Test
    void lapsedCalibrationStampsDegradedValidAndStillWrites() {
        MonitoringPlanEntity plan = activePlan();
        String device = UUID.randomUUID().toString();
        DeviceAssignmentEntity assignment = assignmentService.assign(
                new DeviceAssignmentService.AssignCommand(tenant, plan.getId(), device, "ASSET-1",
                        "ISSUED_TO_PATIENT", "nurse-1", null), null);
        assignmentService.activate(assignment.getId(), "nurse-1", true, null);
        // Calibration lapses AFTER assignment; the resolve gate projects it fresh.
        when(assetRegistryClient.calibrationState(any(), any(), any()))
                .thenReturn(EquipmentCalibration.found("ACTIVE", LocalDate.now().minusDays(3)));

        TelemetryReadingEntity row = ingestionService.ingest(
                reading(device, "systolic_bp", "165", OffsetDateTime.now(), null)).reading();

        assertThat(row.getStatus()).isEqualTo(ReadingStatus.DEGRADED_VALID);
        assertThat(row.getPatientCpid()).isEqualTo(plan.getPatientCpid());
        assertThat(row.getQualityStamp()).contains("DEGRADED").contains("CALIBRATION_LAPSED");
        // Stamped, never dropped: it still reaches the single writer.
        assertThat(row.getShrWriteState()).isEqualTo(ShrWriteState.WRITTEN);
        assertThat(readingEvents(row.getId())).containsExactly("telemonitoring.observation.recorded.v1");
    }

    @Test
    void unverifiableAssetStampsDegradedValid() {
        MonitoringPlanEntity plan = activePlan();
        String device = UUID.randomUUID().toString();
        DeviceAssignmentEntity assignment = assignmentService.assign(
                new DeviceAssignmentService.AssignCommand(tenant, plan.getId(), device, "ASSET-2",
                        "ISSUED_TO_PATIENT", "nurse-1", null), null);
        assignmentService.activate(assignment.getId(), "nurse-1", true, null);
        when(assetRegistryClient.calibrationState(any(), any(), any()))
                .thenReturn(EquipmentCalibration.unreachable());

        TelemetryReadingEntity row = ingestionService.ingest(
                reading(device, "systolic_bp", "132", OffsetDateTime.now(), null)).reading();

        assertThat(row.getStatus()).isEqualTo(ReadingStatus.DEGRADED_VALID);
        assertThat(row.getQualityStamp()).contains("UNVERIFIED");
    }

    @Test
    void okCalibrationYieldsValidWrittenReading() {
        MonitoringPlanEntity plan = activePlan();
        String device = UUID.randomUUID().toString();
        activeAssignment(plan, device);

        TelemetryReadingEntity row = ingestionService.ingest(
                reading(device, "systolic_bp", "128.5", OffsetDateTime.now(), null)).reading();

        assertThat(row.getStatus()).isEqualTo(ReadingStatus.VALID);
        assertThat(row.getPatientCpid()).isEqualTo(plan.getPatientCpid());
        assertThat(row.getPlanId()).isEqualTo(plan.getId());
        assertThat(row.getMetricValue()).isEqualByComparingTo("128.5");
        assertThat(row.getShrWriteState()).isEqualTo(ShrWriteState.WRITTEN);
        assertThat(row.getShrWrittenAt()).isNotNull();
    }

    // ── Journey #64 (ingest half): replayed readings are no-ops ──

    @Test
    void replayedReadingIsAuditCountedNoOp() {
        MonitoringPlanEntity plan = activePlan();
        String device = UUID.randomUUID().toString();
        activeAssignment(plan, device);
        OffsetDateTime measuredAt = OffsetDateTime.now();

        IngestOutcome first = ingestionService.ingest(reading(device, "systolic_bp", "150", measuredAt, null));
        IngestOutcome replay = ingestionService.ingest(reading(device, "systolic_bp", "150", measuredAt, null));

        assertThat(first.deduplicated()).isFalse();
        assertThat(replay.deduplicated()).isTrue();
        assertThat(replay.reading().getId()).isEqualTo(first.reading().getId());
        long rows = readingRepository.findAll().stream()
                .filter(r -> device.equals(r.getDeviceRef())).count();
        assertThat(rows).isEqualTo(1);
        // Exactly one SHR write for the one clinical fact.
        verify(fhirGatewayClient, org.mockito.Mockito.times(1))
                .forwardResource(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sameDeviceDifferentTimestampIsNotADuplicate() {
        MonitoringPlanEntity plan = activePlan();
        String device = UUID.randomUUID().toString();
        activeAssignment(plan, device);
        OffsetDateTime t1 = OffsetDateTime.now().minusMinutes(10);

        IngestOutcome first = ingestionService.ingest(reading(device, "systolic_bp", "150", t1, null));
        IngestOutcome second = ingestionService.ingest(reading(device, "systolic_bp", "151", t1.plusMinutes(5), null));

        assertThat(first.deduplicated()).isFalse();
        assertThat(second.deduplicated()).isFalse();
        assertThat(second.reading().getId()).isNotEqualTo(first.reading().getId());
    }

    // ── Consumer malformed-payload tolerance (bus poison-pill defence) ──

    @Test
    void consumerToleratesMalformedPayloadsWithoutThrowing() {
        TelemetryReadingConsumer consumer = new TelemetryReadingConsumer(ingestionService, new ObjectMapper());
        long before = readingRepository.count();

        consumer.consume("this is not json at all {{{");
        consumer.consume("null");
        consumer.consume("{}");
        consumer.consume("{\"device_id\":\"d1\"}"); // missing metric/timestamp/tenant
        consumer.consume("{\"device_id\":\"d1\",\"metric_type\":\"systolic_bp\","
                + "\"recorded_at\":\"not-a-timestamp\",\"tenant_id\":\"" + tenant + "\"}");
        consumer.consume("{\"device_id\":\"d1\",\"metric_type\":\"systolic_bp\","
                + "\"recorded_at\":\"2026-07-23T10:00:00Z\",\"tenant_id\":\"not-a-uuid\"}");

        assertThat(readingRepository.count()).isEqualTo(before);
    }

    @Test
    void consumerIngestsTheIotBusPayloadShape() {
        MonitoringPlanEntity plan = activePlan();
        String device = UUID.randomUUID().toString();
        activeAssignment(plan, device);
        TelemetryReadingConsumer consumer = new TelemetryReadingConsumer(ingestionService, new ObjectMapper());
        String sourceReadingId = UUID.randomUUID().toString();

        // Exact field set emitted by iot-ingestion's impilo.iot.telemetry.reading.ingested.v1.
        consumer.consume("{"
                + "\"reading_id\":\"" + sourceReadingId + "\","
                + "\"tenant_id\":\"" + tenant + "\","
                + "\"device_id\":\"" + device + "\","
                + "\"metric_type\":\"systolic_bp\","
                + "\"metric_value\":\"142\","
                + "\"unit\":\"mmHg\","
                + "\"recorded_at\":\"2026-07-23T10:15:30+02:00\","
                + "\"source\":\"HTTP\"}");

        TelemetryReadingEntity row = readingRepository.findAll().stream()
                .filter(r -> device.equals(r.getDeviceRef()))
                .findFirst().orElseThrow();
        assertThat(row.getSourceReadingId()).isEqualTo(sourceReadingId);
        assertThat(row.getStatus()).isEqualTo(ReadingStatus.VALID);
        assertThat(row.getMetricValue()).isEqualByComparingTo("142");
        assertThat(row.getUnit()).isEqualTo("mmHg");
        assertThat(row.getSource()).isEqualTo("HTTP");
    }
}
