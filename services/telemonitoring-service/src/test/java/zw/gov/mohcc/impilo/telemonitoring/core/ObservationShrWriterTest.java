package zw.gov.mohcc.impilo.telemonitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.telemonitoring.domain.PlanStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.ReadingStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.ShrWriteState;
import zw.gov.mohcc.impilo.telemonitoring.integration.AssetRegistryClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.FhirGatewayClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.FhirGatewayClient.ForwardResult;
import zw.gov.mohcc.impilo.telemonitoring.integration.FhirGatewayClient.Outcome;
import zw.gov.mohcc.impilo.telemonitoring.integration.IotDeviceRegistryClient;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.TelemetryReadingEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.TelemetryReadingRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OF-B25 — single-writer honesty law, bounded retry, FHIR payload shape and the
 * writer-side idempotency key (journey #64 second half). The gateway client is mocked;
 * everything else (plan gate, ledger columns, outbox events) runs for real on H2.
 */
@SpringBootTest
@ActiveProfiles("test")
class ObservationShrWriterTest {

    @Autowired private ObservationShrWriter shrWriter;
    @Autowired private MonitoringPlanService planService;
    @Autowired private MonitoringProgrammeRepository programmeRepository;
    @Autowired private TelemetryReadingRepository readingRepository;
    @Autowired private EventOutboxRepository outboxRepository;

    @MockBean private IotDeviceRegistryClient iotDeviceRegistryClient;
    @MockBean private AssetRegistryClient assetRegistryClient;
    @MockBean private FhirGatewayClient fhirGatewayClient;

    private final UUID tenant = UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void seedProgramme() {
        if (programmeRepository.findByCode("HYPERTENSION").isEmpty()) {
            MonitoringProgrammeEntity p = new MonitoringProgrammeEntity();
            p.setCode("HYPERTENSION");
            p.setName("Hypertension");
            p.setDefaultThresholds("{\"systolic_bp\":{\"greenMax\":140}}");
            programmeRepository.save(p);
        }
    }

    private MonitoringPlanEntity activePlan() {
        MonitoringPlanEntity plan = planService.createDraft(new MonitoringPlanService.CreatePlanCommand(
                tenant, "CPID-" + UUID.randomUUID(), "HYPERTENSION", null,
                "Essential hypertension", "HOME_SELF_MONITORING", "WEEKLY", null, null,
                null, null, "clinician-a"), true);
        return planService.approve(plan.getId(), "clinician-b");
    }

    private TelemetryReadingEntity pendingReading(MonitoringPlanEntity plan, String metric, String value,
                                                  ReadingStatus status) {
        TelemetryReadingEntity reading = new TelemetryReadingEntity();
        reading.setTenantId(tenant);
        reading.setDeviceRef("device-" + UUID.randomUUID());
        reading.setPlanId(plan.getId());
        reading.setAssignmentId(UUID.randomUUID());
        reading.setPatientCpid(plan.getPatientCpid());
        reading.setMetricCode(metric);
        reading.setMetricValue(new BigDecimal(value));
        reading.setUnit("mmHg");
        reading.setMeasuredAt(OffsetDateTime.parse("2026-07-23T09:30:00+02:00"));
        reading.setSource("HTTP");
        reading.setQualityStamp("{\"trustLevel\":\"UNGRADED\",\"calibrationPosture\":\"OK\",\"source\":\"HTTP\"}");
        reading.setDedupKey(ReadingIngestionService.dedupKey(
                reading.getDeviceRef(), metric, reading.getMeasuredAt()));
        reading.setStatus(status);
        reading.setShrWriteState(ShrWriteState.PENDING);
        return readingRepository.save(reading);
    }

    private List<String> events(UUID readingId) {
        return outboxRepository.findAll().stream()
                .filter(e -> readingId.toString().equals(e.getAggregateId()))
                .map(zw.gov.mohcc.impilo.telemonitoring.persistence.entity.EventOutboxEntity::getEventType)
                .toList();
    }

    private static ForwardResult success() {
        return new ForwardResult(Outcome.SUCCESS, "fhir-gateway:audit:42", "SUCCESS");
    }

    private static ForwardResult failure(Outcome outcome) {
        return new ForwardResult(outcome, null, outcome.name());
    }

    // ── Honesty law ──

    @Test
    void gatewaySuccessMarksWrittenWithRefAndEmitsRecorded() {
        when(fhirGatewayClient.forwardResource(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(success());
        TelemetryReadingEntity reading = pendingReading(activePlan(), "systolic_bp", "150", ReadingStatus.VALID);

        shrWriter.attemptWrite(reading);

        TelemetryReadingEntity row = readingRepository.findById(reading.getId()).orElseThrow();
        assertThat(row.getShrWriteState()).isEqualTo(ShrWriteState.WRITTEN);
        assertThat(row.getShrWrittenAt()).isNotNull();
        assertThat(row.getShrRef()).isEqualTo("fhir-gateway:audit:42");
        assertThat(events(reading.getId())).containsExactly("telemonitoring.observation.recorded.v1");
    }

    @Test
    void forwardFailedLeavesRecordUnwrittenEmitsCodedEventAndRetries() {
        when(fhirGatewayClient.forwardResource(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(failure(Outcome.FORWARD_FAILED));
        TelemetryReadingEntity reading = pendingReading(activePlan(), "systolic_bp", "150", ReadingStatus.VALID);

        shrWriter.attemptWrite(reading);

        TelemetryReadingEntity row = readingRepository.findById(reading.getId()).orElseThrow();
        assertThat(row.getShrWrittenAt()).isNull();
        assertThat(row.getShrRef()).isNull();
        assertThat(row.getShrWriteState()).isEqualTo(ShrWriteState.RETRYING);
        assertThat(row.getShrWriteAttempts()).isEqualTo(1);
        assertThat(row.getShrLastWriteError()).contains("FORWARD_FAILED");
        assertThat(events(reading.getId())).containsExactly("telemonitoring.observation.write_failed.v1");

        // The sweep re-attempts; when the gateway recovers the reading is finally written.
        when(fhirGatewayClient.forwardResource(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(success());
        int attempted = shrWriter.retryPending();

        assertThat(attempted).isGreaterThanOrEqualTo(1);
        row = readingRepository.findById(reading.getId()).orElseThrow();
        assertThat(row.getShrWriteState()).isEqualTo(ShrWriteState.WRITTEN);
        assertThat(row.getShrWrittenAt()).isNotNull();
        assertThat(events(reading.getId())).containsExactly(
                "telemonitoring.observation.write_failed.v1",
                "telemonitoring.observation.recorded.v1");
    }

    @Test
    void noRouteAndConsentDeniedAreCodedNeverMarkedWritten() {
        TelemetryReadingEntity r1 = pendingReading(activePlan(), "systolic_bp", "150", ReadingStatus.VALID);
        TelemetryReadingEntity r2 = pendingReading(activePlan(), "systolic_bp", "151", ReadingStatus.VALID);

        when(fhirGatewayClient.forwardResource(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(failure(Outcome.NO_ROUTE));
        shrWriter.attemptWrite(r1);
        when(fhirGatewayClient.forwardResource(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(failure(Outcome.CONSENT_DENIED));
        shrWriter.attemptWrite(r2);

        assertThat(readingRepository.findById(r1.getId()).orElseThrow().getShrLastWriteError())
                .contains("NO_ROUTE");
        assertThat(readingRepository.findById(r2.getId()).orElseThrow().getShrLastWriteError())
                .contains("CONSENT_DENIED");
        assertThat(readingRepository.findById(r1.getId()).orElseThrow().getShrWrittenAt()).isNull();
        assertThat(readingRepository.findById(r2.getId()).orElseThrow().getShrWrittenAt()).isNull();
    }

    @Test
    void retriesAreBoundedAndParkExhausted() {
        when(fhirGatewayClient.forwardResource(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(failure(Outcome.UNREACHABLE));
        TelemetryReadingEntity reading = pendingReading(activePlan(), "systolic_bp", "150", ReadingStatus.VALID);

        for (int i = 0; i < 7; i++) { // more sweeps than the attempt budget (5)
            shrWriter.attemptWrite(readingRepository.findById(reading.getId()).orElseThrow());
        }

        TelemetryReadingEntity row = readingRepository.findById(reading.getId()).orElseThrow();
        assertThat(row.getShrWriteState()).isEqualTo(ShrWriteState.EXHAUSTED);
        assertThat(row.getShrWriteAttempts()).isEqualTo(5);
        assertThat(row.getShrWrittenAt()).isNull();
        // EXHAUSTED rows stop consuming the gateway: exactly maxAttempts calls happened.
        verify(fhirGatewayClient, times(5)).forwardResource(any(), any(), any(), any(), any(), any(), any());
    }

    // ── Eligibility gates ──

    @Test
    void planNotActiveIsNotEligibleAndGatewayNeverCalled() {
        MonitoringPlanEntity plan = activePlan();
        planService.suspend(plan.getId(), "Patient admitted", "clinician-b");
        TelemetryReadingEntity reading = pendingReading(plan, "systolic_bp", "150", ReadingStatus.VALID);

        shrWriter.attemptWrite(reading);

        TelemetryReadingEntity row = readingRepository.findById(reading.getId()).orElseThrow();
        assertThat(row.getShrWriteState()).isEqualTo(ShrWriteState.NOT_ELIGIBLE);
        assertThat(row.getShrWrittenAt()).isNull();
        verify(fhirGatewayClient, never()).forwardResource(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void quarantinedReadingNeverReachesTheGateway() {
        TelemetryReadingEntity reading = new TelemetryReadingEntity();
        reading.setTenantId(tenant);
        reading.setDeviceRef("device-" + UUID.randomUUID());
        reading.setMetricCode("systolic_bp");
        reading.setMetricValue(new BigDecimal("150"));
        reading.setMeasuredAt(OffsetDateTime.now());
        reading.setDedupKey(ReadingIngestionService.dedupKey(reading.getDeviceRef(), "systolic_bp",
                reading.getMeasuredAt()));
        reading.setStatus(ReadingStatus.QUARANTINED);
        reading.setQuarantineReason(ReadingIngestionService.REASON_NO_ASSIGNMENT);
        reading.setShrWriteState(ShrWriteState.NOT_ELIGIBLE);
        reading = readingRepository.save(reading);

        shrWriter.attemptWrite(reading);

        verify(fhirGatewayClient, never()).forwardResource(any(), any(), any(), any(), any(), any(), any());
    }

    // ── FHIR payload shape + writer-side idempotency (journey #64, second half) ──

    @Test
    void observationPayloadIsCpidOnlyCodedAndStampCarrying() throws Exception {
        when(fhirGatewayClient.forwardResource(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(success());
        MonitoringPlanEntity plan = activePlan();
        TelemetryReadingEntity reading = pendingReading(plan, "systolic_bp", "150", ReadingStatus.VALID);

        shrWriter.attemptWrite(reading);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> idempotencyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fhirGatewayClient).forwardResource(eq(tenant), eq("Observation"), eq("CREATE"),
                payloadCaptor.capture(), eq(plan.getPatientCpid()), eq("TREATMENT"),
                idempotencyCaptor.capture());

        // Idempotency-Key at the writer == the reading's dedup key (double-write defence).
        assertThat(idempotencyCaptor.getValue()).isEqualTo(reading.getDedupKey());

        JsonNode obs = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(obs.get("resourceType").asText()).isEqualTo("Observation");
        assertThat(obs.get("status").asText()).isEqualTo("final");
        // CPID-only subject — no name, no demographics, nothing but the reference. It is a match
        // URL, not "Patient/<cpid>": a CPID is not a FHIR logical id, and BUTANO enforces
        // referential integrity on write, so the literal form is a dangling reference the SHR
        // rejects with HAPI-1094. See ObservationShrWriter.patientSubjectReference.
        assertThat(obs.get("subject").get("reference").asText())
                .isEqualTo("Patient?identifier=https://impilo.gov.zw/cpid|" + plan.getPatientCpid());
        assertThat(obs.get("subject").get("reference").asText())
                .describedAs("a literal Patient/<cpid> reference does not resolve in the SHR")
                .doesNotStartWith("Patient/");
        assertThat(payloadCaptor.getValue()).doesNotContain("name").doesNotContain("birthDate");
        // Governed LOINC coding for a known metric.
        JsonNode coding = obs.get("code").get("coding").get(0);
        assertThat(coding.get("system").asText()).isEqualTo(MetricCodeRegistry.LOINC);
        assertThat(coding.get("code").asText()).isEqualTo("8480-6");
        assertThat(obs.get("valueQuantity").get("value").decimalValue()).isEqualByComparingTo("150");
        assertThat(obs.get("valueQuantity").get("unit").asText()).isEqualTo("mmHg");
        assertThat(obs.get("effectiveDateTime").asText()).isEqualTo("2026-07-23T09:30+02:00");
        // The quality stamp travels (§15.7), and the plan/device provenance rides along.
        assertThat(obs.get("identifier").get(0).get("value").asText()).isEqualTo(reading.getDedupKey());
        String extensions = obs.get("extension").toString();
        assertThat(extensions).contains(ObservationShrWriter.EXT_QUALITY_STAMP)
                .contains("UNGRADED")
                .contains(plan.getId().toString());
        assertThat(obs.get("device").get("display").asText()).contains(reading.getDeviceRef());
    }

    @Test
    void degradedValidReadingIsWrittenAsPreliminaryWithItsStamp() throws Exception {
        when(fhirGatewayClient.forwardResource(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(success());
        MonitoringPlanEntity plan = activePlan();
        TelemetryReadingEntity reading = pendingReading(plan, "systolic_bp", "150", ReadingStatus.DEGRADED_VALID);
        reading.setQualityStamp("{\"trustLevel\":\"UNGRADED\",\"calibrationPosture\":\"DEGRADED\","
                + "\"calibrationReason\":\"CALIBRATION_LAPSED\"}");
        reading = readingRepository.save(reading);

        shrWriter.attemptWrite(reading);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(fhirGatewayClient).forwardResource(any(), any(), any(), payloadCaptor.capture(),
                any(), any(), any());
        JsonNode obs = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(obs.get("status").asText()).isEqualTo("preliminary");
        assertThat(payloadCaptor.getValue()).contains("CALIBRATION_LAPSED");
        assertThat(readingRepository.findById(reading.getId()).orElseThrow().getShrWriteState())
                .isEqualTo(ShrWriteState.WRITTEN);
    }

    @Test
    void unknownMetricIsCodedUnderTheHonestLocalSystem() throws Exception {
        when(fhirGatewayClient.forwardResource(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(success());
        TelemetryReadingEntity reading = pendingReading(activePlan(), "vendor_custom_metric_x", "7",
                ReadingStatus.VALID);

        shrWriter.attemptWrite(reading);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(fhirGatewayClient).forwardResource(any(), any(), any(), payloadCaptor.capture(),
                any(), any(), any());
        JsonNode coding = objectMapper.readTree(payloadCaptor.getValue()).get("code").get("coding").get(0);
        assertThat(coding.get("system").asText()).isEqualTo(MetricCodeRegistry.LOCAL_SYSTEM);
        assertThat(coding.get("code").asText()).isEqualTo("vendor_custom_metric_x");
    }
}
