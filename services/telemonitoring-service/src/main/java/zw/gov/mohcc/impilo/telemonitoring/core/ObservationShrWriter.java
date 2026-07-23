package zw.gov.mohcc.impilo.telemonitoring.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.telemonitoring.domain.PlanStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.ReadingStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.ShrWriteState;
import zw.gov.mohcc.impilo.telemonitoring.events.TelemonitoringEventEmitter;
import zw.gov.mohcc.impilo.telemonitoring.integration.FhirGatewayClient;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.TelemetryReadingEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringPlanRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.TelemetryReadingRepository;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single designated writer of monitoring-band Observations (OF-B25, §14.5 steps
 * 12–14). Eligibility: reading status VALID or DEGRADED_VALID (the stamp travels — a
 * DEGRADED_VALID reading is written WITH its stamp, and OF-B26 gates it out of
 * value-alerting; it is never dropped) AND the reading's plan is ACTIVE. Quarantined
 * readings never reach this class' gateway call (journey #62).
 *
 * <h3>Honesty law</h3>
 * <p>{@code shr_written_at}/{@code shr_ref} are set ONLY on gateway outcome SUCCESS.
 * NO_ROUTE / FORWARD_FAILED / CONSENT_DENIED / transport failure leave the row
 * unwritten, emit {@code telemonitoring.observation.write_failed.v1} with the coded
 * outcome, and the bounded {@code @Scheduled} sweep (ObservationWriteRetrySweep)
 * re-attempts until the attempt cap parks the row EXHAUSTED — visible, never silently
 * absorbed. Success emits {@code telemonitoring.observation.recorded.v1}.</p>
 *
 * <h3>Idempotency (journey #64, second half)</h3>
 * <p>The forward carries {@code Idempotency-Key} = the reading's dedup key, so a
 * retried write is recognisable end to end as the same clinical fact.</p>
 */
@Service
public class ObservationShrWriter {

    private static final Logger log = LoggerFactory.getLogger(ObservationShrWriter.class);

    /** Local extension/identifier namespaces — honest provenance carriage, CPID-only. */
    static final String EXT_QUALITY_STAMP = "https://impilo.mohcc.gov.zw/fhir/StructureDefinition/telemonitoring-quality-stamp";
    static final String EXT_PLAN_ID = "https://impilo.mohcc.gov.zw/fhir/StructureDefinition/telemonitoring-plan-id";
    static final String IDENTIFIER_SYSTEM = "https://impilo.mohcc.gov.zw/fhir/NamingSystem/telemonitoring-reading";

    private final TelemetryReadingRepository readingRepository;
    private final MonitoringPlanRepository planRepository;
    private final FhirGatewayClient fhirGatewayClient;
    private final MetricCodeRegistry metricCodeRegistry;
    private final TelemonitoringEventEmitter eventEmitter;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    public ObservationShrWriter(TelemetryReadingRepository readingRepository,
                                MonitoringPlanRepository planRepository,
                                FhirGatewayClient fhirGatewayClient,
                                MetricCodeRegistry metricCodeRegistry,
                                TelemonitoringEventEmitter eventEmitter,
                                ObjectMapper objectMapper,
                                @Value("${telemonitoring.shr.max-write-attempts:5}") int maxAttempts) {
        this.readingRepository = readingRepository;
        this.planRepository = planRepository;
        this.fhirGatewayClient = fhirGatewayClient;
        this.metricCodeRegistry = metricCodeRegistry;
        this.eventEmitter = eventEmitter;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Attempt the SHR write for one reading. Safe to call repeatedly: WRITTEN rows and
     * quarantined rows are no-ops, and the plan-ACTIVE gate is re-evaluated per attempt.
     */
    @Transactional
    public void attemptWrite(TelemetryReadingEntity reading) {
        if (reading.getStatus() == ReadingStatus.QUARANTINED
                || reading.getShrWriteState() == ShrWriteState.WRITTEN
                || reading.getShrWriteState() == ShrWriteState.EXHAUSTED) {
            return;
        }
        if (reading.getPlanId() == null || reading.getPatientCpid() == null) {
            park(reading, "reading carries no plan/patient attribution");
            return;
        }
        MonitoringPlanEntity plan = planRepository.findById(reading.getPlanId()).orElse(null);
        if (plan == null || plan.getStatus() != PlanStatus.ACTIVE) {
            park(reading, "plan " + reading.getPlanId() + " is "
                    + (plan == null ? "missing" : plan.getStatus()) + " — only ACTIVE plans project to the SHR");
            return;
        }

        String observationJson = buildObservationJson(reading);
        FhirGatewayClient.ForwardResult result = fhirGatewayClient.forwardResource(
                reading.getTenantId(), "Observation", "CREATE", observationJson,
                reading.getPatientCpid(), "TREATMENT", reading.getDedupKey());

        if (result.outcome() == FhirGatewayClient.Outcome.SUCCESS) {
            reading.setShrWrittenAt(OffsetDateTime.now());
            reading.setShrRef(result.auditRef());
            reading.setShrWriteState(ShrWriteState.WRITTEN);
            reading.setShrLastWriteError(null);
            readingRepository.save(reading);

            Map<String, Object> payload = basePayload(reading);
            payload.put("shrRef", result.auditRef());
            payload.put("status", reading.getStatus().name());
            eventEmitter.emitObservationEvent("recorded", reading.getId(), reading.getPatientCpid(),
                    payload, reading.getTenantId());
            log.info("SHR write SUCCESS for reading {} (metric={}, plan={})",
                    reading.getId(), reading.getMetricCode(), reading.getPlanId());
            return;
        }

        // Honesty law: anything but SUCCESS leaves the record unwritten.
        int attempts = reading.getShrWriteAttempts() + 1;
        reading.setShrWriteAttempts(attempts);
        reading.setShrLastWriteError(result.outcome().name()
                + (result.detail() != null ? ": " + result.detail() : ""));
        reading.setShrWriteState(attempts >= maxAttempts ? ShrWriteState.EXHAUSTED : ShrWriteState.RETRYING);
        readingRepository.save(reading);

        Map<String, Object> payload = basePayload(reading);
        payload.put("outcome", result.outcome().name());
        payload.put("detail", result.detail());
        payload.put("attempts", attempts);
        payload.put("exhausted", reading.getShrWriteState() == ShrWriteState.EXHAUSTED);
        eventEmitter.emitObservationEvent("write_failed", reading.getId(), reading.getPatientCpid(),
                payload, reading.getTenantId());
        log.warn("SHR write {} for reading {} (attempt {}/{}) — Observation NOT in the record",
                result.outcome(), reading.getId(), attempts, maxAttempts);
    }

    /** Bounded retry pass over PENDING/RETRYING rows; returns how many were attempted. */
    @Transactional
    public int retryPending() {
        List<TelemetryReadingEntity> due = readingRepository
                .findTop100ByShrWriteStateInOrderByCreatedAtAsc(
                        EnumSet.of(ShrWriteState.PENDING, ShrWriteState.RETRYING));
        for (TelemetryReadingEntity reading : due) {
            attemptWrite(reading);
        }
        return due.size();
    }

    /**
     * FHIR R4 Observation, monitoring band: coded metric (LOINC where governed, honest
     * local system otherwise), CPID-only subject, original device time as
     * effectiveDateTime, valueQuantity, and the §15.7 quality stamp + device/plan
     * provenance as extensions. No PII beyond the CPID reference ever enters this payload.
     */
    String buildObservationJson(TelemetryReadingEntity reading) {
        MetricCodeRegistry.MetricCoding coding = metricCodeRegistry.resolve(reading.getMetricCode());

        ObjectNode obs = objectMapper.createObjectNode();
        obs.put("resourceType", "Observation");
        // DEGRADED_VALID surfaces as preliminary — the stamp travels, the value is not hidden.
        obs.put("status", reading.getStatus() == ReadingStatus.DEGRADED_VALID ? "preliminary" : "final");

        ArrayNode identifiers = obs.putArray("identifier");
        ObjectNode identifier = identifiers.addObject();
        identifier.put("system", IDENTIFIER_SYSTEM);
        identifier.put("value", reading.getDedupKey());

        ObjectNode category = obs.putArray("category").addObject();
        ObjectNode categoryCoding = category.putArray("coding").addObject();
        categoryCoding.put("system", "http://terminology.hl7.org/CodeSystem/observation-category");
        categoryCoding.put("code", "vital-signs");
        category.put("text", "Community telemonitoring");

        ObjectNode code = obs.putObject("code");
        ObjectNode codeCoding = code.putArray("coding").addObject();
        codeCoding.put("system", coding.system());
        codeCoding.put("code", coding.code());
        codeCoding.put("display", coding.display());
        code.put("text", reading.getMetricCode());

        // CPID-only subject (No-PII-in-SHR rule).
        obs.putObject("subject").put("reference", "Patient/" + reading.getPatientCpid());
        obs.put("effectiveDateTime", reading.getMeasuredAt().toString());

        ObjectNode value = obs.putObject("valueQuantity");
        value.put("value", reading.getMetricValue());
        if (reading.getUnit() != null) {
            value.put("unit", reading.getUnit());
        }

        obs.putObject("device").put("display", "iot-device:" + reading.getDeviceRef());

        ArrayNode extensions = obs.putArray("extension");
        ObjectNode stampExt = extensions.addObject();
        stampExt.put("url", EXT_QUALITY_STAMP);
        stampExt.put("valueString", reading.getQualityStamp() != null ? reading.getQualityStamp() : "{}");
        ObjectNode planExt = extensions.addObject();
        planExt.put("url", EXT_PLAN_ID);
        planExt.put("valueString", reading.getPlanId().toString());

        return obs.toString();
    }

    private void park(TelemetryReadingEntity reading, String why) {
        reading.setShrWriteState(ShrWriteState.NOT_ELIGIBLE);
        reading.setShrLastWriteError("NOT_ELIGIBLE: " + why);
        readingRepository.save(reading);
        log.info("Reading {} not eligible for SHR write: {}", reading.getId(), why);
    }

    private static Map<String, Object> basePayload(TelemetryReadingEntity reading) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("readingId", reading.getId().toString());
        payload.put("deviceRef", reading.getDeviceRef());
        payload.put("planId", reading.getPlanId() != null ? reading.getPlanId().toString() : null);
        payload.put("metricCode", reading.getMetricCode());
        payload.put("dedupKey", reading.getDedupKey());
        return payload;
    }
}
