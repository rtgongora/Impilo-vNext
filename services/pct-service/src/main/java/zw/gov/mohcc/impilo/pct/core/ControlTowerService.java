package zw.gov.mohcc.impilo.pct.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.config.PctProperties;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.domain.QueueItemStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.DischargeCaseEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueItemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.AdmissionRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.DischargeCaseRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueItemRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates real-time operational data for the facility control tower dashboard.
 *
 * <p>The control tower provides a bird's-eye view of facility operations,
 * combining queue metrics, admission occupancy, discharge bottlenecks,
 * throughput statistics, and active journey counts into a single snapshot.
 * This data powers the ops-console real-time dashboard and feeds into
 * SLA monitoring and alerting systems.</p>
 *
 * <p>All queries are scoped to a single facility identified by
 * {@code facilityId}. The snapshot is computed on-demand and is not
 * cached — callers should implement their own caching strategy if
 * sub-second response times are required for high-frequency polling.</p>
 */
@Service
public class ControlTowerService {

    private static final Logger log = LoggerFactory.getLogger(ControlTowerService.class);

    /**
     * Journey states considered non-terminal (active).
     */
    private static final Set<JourneyState> ACTIVE_STATES = EnumSet.of(
            JourneyState.REGISTRATION_PENDING,
            JourneyState.ARRIVED,
            JourneyState.TRIAGED,
            JourneyState.QUEUED,
            JourneyState.IN_SERVICE,
            JourneyState.ADMITTED,
            JourneyState.TRANSFERRED,
            JourneyState.DISCHARGE_PENDING
    );

    /**
     * Terminal journey states that count toward throughput.
     */
    private static final Set<JourneyState> THROUGHPUT_STATES = EnumSet.of(
            JourneyState.DISCHARGED
    );

    /** Queue item statuses that indicate an active queue presence. */
    private static final Collection<QueueItemStatus> ACTIVE_QUEUE_STATUSES = EnumSet.of(
            QueueItemStatus.WAITING, QueueItemStatus.CALLED, QueueItemStatus.IN_SERVICE
    );

    /** Admission statuses considered active for bed occupancy. */
    private static final List<String> ACTIVE_ADMISSION_STATUSES = List.of("ADMITTED");

    /** Discharge statuses considered stuck (not yet completed). */
    private static final List<String> STUCK_DISCHARGE_STATUSES = List.of("INITIATED");

    private final QueueItemRepository queueItemRepository;
    private final JourneyRepository journeyRepository;
    private final AdmissionRepository admissionRepository;
    private final DischargeCaseRepository dischargeCaseRepository;
    private final PctProperties pctProperties;

    public ControlTowerService(QueueItemRepository queueItemRepository,
                               JourneyRepository journeyRepository,
                               AdmissionRepository admissionRepository,
                               DischargeCaseRepository dischargeCaseRepository,
                               PctProperties pctProperties) {
        this.queueItemRepository = queueItemRepository;
        this.journeyRepository = journeyRepository;
        this.admissionRepository = admissionRepository;
        this.dischargeCaseRepository = dischargeCaseRepository;
        this.pctProperties = pctProperties;
    }

    /**
     * Compute a real-time operational snapshot for a facility.
     *
     * <p>Aggregates data from queue items, journeys, admissions, and
     * discharge cases to produce a comprehensive view of facility
     * operations. The snapshot includes:</p>
     * <ul>
     *   <li><strong>queueStats</strong> — per-queue waiting/in-service counts, average wait, SLA breaches</li>
     *   <li><strong>admissionOccupancy</strong> — per-ward occupied bed count</li>
     *   <li><strong>dischargeBlockers</strong> — discharge cases stuck in INITIATED status</li>
     *   <li><strong>throughputToday</strong> — count of journeys reaching DISCHARGED today</li>
     *   <li><strong>activeJourneys</strong> — count of journeys by non-terminal state</li>
     *   <li><strong>bottlenecks</strong> — automatically detected operational bottlenecks</li>
     * </ul>
     *
     * @param facilityId the facility to generate the snapshot for
     * @return a complete control tower snapshot
     */
    @Transactional(readOnly = true)
    public ControlTowerSnapshot getControlTowerData(UUID facilityId) {
        log.debug("Computing control tower snapshot for facility {}", facilityId);

        List<QueueMetrics> queueStats = computeQueueStats(facilityId);
        Map<UUID, WardOccupancy> admissionOccupancy = computeAdmissionOccupancy(facilityId);
        List<DischargeBlockerInfo> dischargeBlockers = computeDischargeBlockers(facilityId);
        long throughputToday = computeThroughputToday(facilityId);
        Map<String, Long> activeJourneys = computeActiveJourneys(facilityId);
        List<BottleneckAlert> bottlenecks = detectBottlenecks(queueStats);

        return new ControlTowerSnapshot(
                queueStats,
                admissionOccupancy,
                dischargeBlockers,
                throughputToday,
                activeJourneys,
                bottlenecks
        );
    }

    // ------------------------------------------------------------------
    // Queue statistics
    // ------------------------------------------------------------------

    private List<QueueMetrics> computeQueueStats(UUID facilityId) {
        // Discover active queues for this facility
        List<UUID> activeQueueIds = queueItemRepository
                .findDistinctQueueIdsByFacilityIdAndStatusIn(facilityId, ACTIVE_QUEUE_STATUSES);

        List<QueueMetrics> metrics = new ArrayList<>();
        long slaThresholdMinutes = pctProperties.getSlaWaitMinutes();

        for (UUID queueId : activeQueueIds) {
            List<QueueItemEntity> waitingItems = queueItemRepository
                    .findByQueueIdAndStatus(queueId, QueueItemStatus.WAITING);

            long waitingCount = waitingItems.size();
            long inServiceCount = queueItemRepository
                    .countByQueueIdAndStatus(queueId, QueueItemStatus.IN_SERVICE);

            // Average wait time for currently waiting items
            double avgWaitMinutes = waitingItems.stream()
                    .mapToLong(item -> Duration.between(
                            item.getCreatedAt().toInstant(), Instant.now()).toMinutes())
                    .average()
                    .orElse(0.0);

            // SLA breach count: items waiting longer than threshold
            long slaBreachCount = waitingItems.stream()
                    .filter(item -> Duration.between(
                            item.getCreatedAt().toInstant(), Instant.now()).toMinutes() > slaThresholdMinutes)
                    .count();

            metrics.add(new QueueMetrics(
                    queueId,
                    waitingCount,
                    Math.round(avgWaitMinutes * 100.0) / 100.0,
                    inServiceCount,
                    slaBreachCount
            ));
        }

        return metrics;
    }

    // ------------------------------------------------------------------
    // Admission occupancy
    // ------------------------------------------------------------------

    private Map<UUID, WardOccupancy> computeAdmissionOccupancy(UUID facilityId) {
        List<AdmissionEntity> activeAdmissions = admissionRepository
                .findByFacilityIdAndStatusIn(facilityId, ACTIVE_ADMISSION_STATUSES);

        return activeAdmissions.stream()
                .filter(a -> a.getWardId() != null)
                .collect(Collectors.groupingBy(
                        AdmissionEntity::getWardId,
                        Collectors.counting()
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new WardOccupancy(entry.getKey(), entry.getValue())
                ));
    }

    // ------------------------------------------------------------------
    // Discharge blockers
    // ------------------------------------------------------------------

    private List<DischargeBlockerInfo> computeDischargeBlockers(UUID facilityId) {
        List<DischargeCaseEntity> stuckCases = dischargeCaseRepository
                .findByFacilityIdAndStatusIn(facilityId, STUCK_DISCHARGE_STATUSES);

        return stuckCases.stream()
                .map(dc -> new DischargeBlockerInfo(
                        dc.getId(),
                        dc.getJourneyId(),
                        dc.getStatus(),
                        dc.getBlockers()
                ))
                .toList();
    }

    // ------------------------------------------------------------------
    // Throughput
    // ------------------------------------------------------------------

    private long computeThroughputToday(UUID facilityId) {
        OffsetDateTime startOfDay = OffsetDateTime.now(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toOffsetDateTime();

        return journeyRepository.countByFacilityIdAndStateAndUpdatedAtAfter(
                facilityId, JourneyState.DISCHARGED, startOfDay);
    }

    // ------------------------------------------------------------------
    // Active journeys by state
    // ------------------------------------------------------------------

    private Map<String, Long> computeActiveJourneys(UUID facilityId) {
        Map<String, Long> counts = new LinkedHashMap<>();

        for (JourneyState state : ACTIVE_STATES) {
            long count = journeyRepository.countByFacilityIdAndState(facilityId, state);
            if (count > 0) {
                counts.put(state.name(), count);
            }
        }

        return counts;
    }

    // ------------------------------------------------------------------
    // Bottleneck detection
    // ------------------------------------------------------------------

    private List<BottleneckAlert> detectBottlenecks(List<QueueMetrics> queueStats) {
        List<BottleneckAlert> alerts = new ArrayList<>();
        long slaThreshold = pctProperties.getSlaWaitMinutes();

        for (QueueMetrics qm : queueStats) {
            // Critical: average wait exceeds 2x SLA
            if (qm.avgWaitMinutes() > slaThreshold * 2) {
                alerts.add(new BottleneckAlert(
                        null, qm.queueId(), "EXCESSIVE_WAIT_TIME", "CRITICAL"));
            }
            // Warning: average wait exceeds SLA
            else if (qm.avgWaitMinutes() > slaThreshold) {
                alerts.add(new BottleneckAlert(
                        null, qm.queueId(), "SLA_BREACH", "WARNING"));
            }

            // Warning: high queue volume
            if (qm.waitingCount() > 50) {
                alerts.add(new BottleneckAlert(
                        null, qm.queueId(), "HIGH_VOLUME", "WARNING"));
            }

            // Warning: high SLA breach ratio
            if (qm.waitingCount() > 0 && qm.slaBreachCount() > qm.waitingCount() / 2) {
                alerts.add(new BottleneckAlert(
                        null, qm.queueId(), "HIGH_SLA_BREACH_RATIO", "WARNING"));
            }
        }

        return alerts;
    }

    // ==================================================================
    // Data transfer records
    // ==================================================================

    /**
     * Immutable snapshot of facility-wide operational data.
     *
     * @param queueStats          per-queue real-time metrics
     * @param admissionOccupancy  per-ward bed occupancy
     * @param dischargeBlockers   discharge cases stuck with unresolved blockers
     * @param throughputToday     count of journeys discharged today (UTC)
     * @param activeJourneys      count of active journeys grouped by state name
     * @param bottlenecks         automatically detected operational bottlenecks
     */
    public record ControlTowerSnapshot(
            List<QueueMetrics> queueStats,
            Map<UUID, WardOccupancy> admissionOccupancy,
            List<DischargeBlockerInfo> dischargeBlockers,
            long throughputToday,
            Map<String, Long> activeJourneys,
            List<BottleneckAlert> bottlenecks
    ) {}

    /**
     * Real-time metrics for a single queue.
     *
     * @param queueId        the queue identifier
     * @param waitingCount   number of items currently in WAITING status
     * @param avgWaitMinutes average wait time in minutes for waiting items
     * @param inServiceCount number of items currently in IN_SERVICE status
     * @param slaBreachCount number of waiting items that exceed the SLA threshold
     */
    public record QueueMetrics(
            UUID queueId,
            long waitingCount,
            double avgWaitMinutes,
            long inServiceCount,
            long slaBreachCount
    ) {}

    /**
     * Ward-level bed occupancy data.
     *
     * @param wardId       the ward identifier
     * @param occupiedBeds number of beds currently occupied by admitted patients
     */
    public record WardOccupancy(
            UUID wardId,
            long occupiedBeds
    ) {}

    /**
     * Information about a discharge case with unresolved blockers.
     *
     * @param caseId    the discharge case identifier
     * @param journeyId the patient journey identifier
     * @param status    the current discharge status
     * @param blockers  JSON array string of remaining blocker names
     */
    public record DischargeBlockerInfo(
            UUID caseId,
            String journeyId,
            String status,
            String blockers
    ) {}

    /**
     * An automatically detected operational bottleneck.
     *
     * @param workspaceId the affected workspace; may be {@code null} for queue-level alerts
     * @param queueId     the affected queue; may be {@code null} for workspace-level alerts
     * @param metric      the bottleneck metric identifier (e.g. EXCESSIVE_WAIT_TIME, HIGH_VOLUME)
     * @param severity    the alert severity (CRITICAL, WARNING, INFO)
     */
    public record BottleneckAlert(
            UUID workspaceId,
            UUID queueId,
            String metric,
            String severity
    ) {}
}
