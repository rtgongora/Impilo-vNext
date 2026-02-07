package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.TriageRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.TriageRecordRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Determines the optimal queue and priority for a patient journey.
 *
 * <p>The routing engine sits between triage/arrival and the queue system.
 * For v1, it accepts an explicit target queue and derives priority from
 * the most recent triage acuity score using an inverse mapping:</p>
 * <ul>
 *   <li>Acuity 1 (most critical) = Priority 5 (highest)</li>
 *   <li>Acuity 2 = Priority 4</li>
 *   <li>Acuity 3 = Priority 3</li>
 *   <li>Acuity 4 = Priority 2</li>
 *   <li>Acuity 5 (least urgent)  = Priority 1 (lowest)</li>
 * </ul>
 *
 * <p>If no triage record exists for the journey, a default priority of 3
 * (medium) is assigned.</p>
 *
 * <p>Future versions will incorporate appointment data, referral source,
 * provider availability, and predictive wait-time models to select the
 * target queue automatically.</p>
 */
@Service
public class RoutingEngine {

    private static final Logger log = LoggerFactory.getLogger(RoutingEngine.class);

    /** Default priority when no triage record is available. */
    private static final int DEFAULT_PRIORITY = 3;

    private final QueueEngine queueEngine;
    private final JourneyRepository journeyRepository;
    private final TriageRecordRepository triageRepository;
    private final TelemetryService telemetryService;
    private final ObjectMapper objectMapper;

    public RoutingEngine(QueueEngine queueEngine,
                         JourneyRepository journeyRepository,
                         TriageRecordRepository triageRepository,
                         TelemetryService telemetryService,
                         ObjectMapper objectMapper) {
        this.queueEngine = queueEngine;
        this.journeyRepository = journeyRepository;
        this.triageRepository = triageRepository;
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Route a patient journey to the specified queue.
     *
     * <p>Looks up the most recent triage record for the journey to determine
     * priority. If a triage record exists, priority is calculated as
     * {@code 6 - acuity} (acuity 1 maps to priority 5). Otherwise, the
     * default priority of 3 is used.</p>
     *
     * <p>The journey is enqueued via the {@link QueueEngine} and a telemetry
     * event is recorded for routing analytics.</p>
     *
     * @param journeyId    the patient journey to route
     * @param targetQueueId the queue to route the journey into
     * @throws IllegalArgumentException if the journey is not found
     */
    @Transactional
    public void route(String journeyId, UUID targetQueueId) {
        JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                .orElseThrow(() -> new IllegalArgumentException("Journey not found: " + journeyId));

        // Derive priority from most recent triage acuity
        int priority = DEFAULT_PRIORITY;
        Optional<TriageRecordEntity> triageOpt = triageRepository
                .findTopByJourneyIdOrderByCreatedAtDesc(journeyId);

        if (triageOpt.isPresent()) {
            int acuity = triageOpt.get().getAcuity();
            priority = 6 - acuity; // acuity 1 -> priority 5, acuity 5 -> priority 1
        }

        // Enqueue via the queue engine (this also transitions the journey to QUEUED)
        queueEngine.enqueue(targetQueueId, journeyId, priority);

        // Telemetry for routing decisions
        Map<String, Object> telemetryData = new LinkedHashMap<>();
        telemetryData.put("journeyId", journeyId);
        telemetryData.put("targetQueueId", targetQueueId.toString());
        telemetryData.put("priority", priority);
        telemetryData.put("triageAvailable", triageOpt.isPresent());
        if (triageOpt.isPresent()) {
            telemetryData.put("triageAcuity", triageOpt.get().getAcuity());
        }
        telemetryData.put("routingStrategy", "EXPLICIT_V1");
        telemetryService.record("routing.decision", journeyId, telemetryData);

        log.info("Routed journey {} to queue {} with priority {} (triage={})",
                journeyId, targetQueueId, priority,
                triageOpt.map(t -> "acuity-" + t.getAcuity()).orElse("none"));
    }
}
