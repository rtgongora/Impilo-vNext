package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.config.OrosProperties;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.SlaTimerEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.SlaTimerRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages SLA timers for order turnaround tracking.
 *
 * <p>Starts timers when orders are placed or enter specific stages, using
 * the configured default turnaround time. A scheduled check detects breaches
 * and publishes SLA_BREACHED events for escalation.</p>
 */
@Service
public class SlaService {

    private static final Logger log = LoggerFactory.getLogger(SlaService.class);

    private final SlaTimerRepository slaTimerRepository;
    private final OrderRepository orderRepository;
    private final EventOutboxRepository outboxRepository;
    private final OrosProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the SlaService with all required dependencies.
     *
     * @param slaTimerRepository repository for SLA timer persistence
     * @param orderRepository    repository for order queries (needed for tenantId lookup)
     * @param outboxRepository   repository for outbox event persistence
     * @param properties         OROS configuration properties
     * @param objectMapper       Jackson mapper for JSON serialization
     */
    public SlaService(SlaTimerRepository slaTimerRepository,
                      OrderRepository orderRepository,
                      EventOutboxRepository outboxRepository,
                      OrosProperties properties,
                      ObjectMapper objectMapper) {
        this.slaTimerRepository = slaTimerRepository;
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public SlaService(SlaTimerRepository slaTimerRepository,
                      OrosProperties properties) {
        this(slaTimerRepository, null, null, properties, null);
    }

    public SlaTimerEntity startTimer(OrderEntity order) {
        return startTimer(order.getOrderId(), "OVERALL", 0);
    }

    /**
     * Start an SLA timer for an order stage with the specified turnaround time.
     *
     * <p>Creates a new timer with the target completion time calculated as
     * the current time plus the specified turnaround minutes. If tatMinutes
     * is zero or negative, the default TAT from configuration is used.</p>
     *
     * @param orderId    the ULID identifier of the order
     * @param stage      the stage name (e.g. "OVERALL", "SPECIMEN_COLLECTION", "ANALYSIS")
     * @param tatMinutes the turnaround time target in minutes; uses default if &lt;= 0
     * @return the created SLA timer entity
     */
    @Transactional
    public SlaTimerEntity startTimer(String orderId, String stage, int tatMinutes) {
        int effectiveTat = tatMinutes > 0 ? tatMinutes : properties.getSla().getDefaultTatMinutes();
        OffsetDateTime now = OffsetDateTime.now();

        SlaTimerEntity timer = new SlaTimerEntity();
        timer.setTimerId(UUID.randomUUID());
        timer.setOrderId(orderId);
        timer.setStage(stage);
        timer.setStartedAt(now);
        timer.setTargetAt(now.plusMinutes(effectiveTat));
        timer.setBreached(false);
        timer = slaTimerRepository.save(timer);

        log.info("SLA timer started: orderId={}, stage={}, targetAt={} ({}min)",
                orderId, stage, timer.getTargetAt(), effectiveTat);

        return timer;
    }

    /**
     * Scheduled check for SLA breaches.
     *
     * <p>Finds all non-breached timers whose target time has passed, marks them
     * as breached with the current timestamp, and publishes an SLA_BREACHED
     * outbox event for each breach. This method is idempotent.</p>
     */
    @Scheduled(fixedDelayString = "${oros.sla.breach-check-interval-ms:60000}")
    @Transactional
    public void checkBreaches() {
        OffsetDateTime now = OffsetDateTime.now();
        List<SlaTimerEntity> breached = slaTimerRepository.findByBreachedFalseAndTargetAtBefore(now);

        if (breached.isEmpty()) {
            return;
        }

        int count = 0;
        for (SlaTimerEntity timer : breached) {
            timer.setBreached(true);
            timer.setBreachedAt(now);
            slaTimerRepository.save(timer);

            // Look up the order to get tenantId (required for outbox NOT NULL constraint)
            OrderEntity order = orderRepository.findByOrderId(timer.getOrderId()).orElse(null);
            if (order == null) {
                log.warn("Order not found for SLA timer {}, skipping outbox event", timer.getTimerId());
                count++;
                continue;
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("timerId", timer.getTimerId().toString());
            payload.put("orderId", timer.getOrderId());
            payload.put("stage", timer.getStage());
            payload.put("startedAt", timer.getStartedAt().toString());
            payload.put("targetAt", timer.getTargetAt().toString());
            payload.put("breachedAt", now.toString());
            payload.put("overageMinutes", Duration.between(timer.getTargetAt(), now).toMinutes());

            try {
                EventOutboxEntity outbox = new EventOutboxEntity();
                outbox.setAggregateType("SLA");
                outbox.setAggregateId(timer.getOrderId());
                outbox.setEventType("SLA_BREACHED");
                outbox.setPayload(objectMapper.writeValueAsString(payload));
                outbox.setTenantId(order.getTenantId());
                outboxRepository.save(outbox);
            } catch (Exception e) {
                log.error("Failed to write SLA_BREACHED outbox event for timer {}: {}",
                        timer.getTimerId(), e.getMessage(), e);
            }

            count++;
        }

        log.warn("SLA breach check completed: {} timers breached", count);
    }

    /**
     * Returns all SLA timers for the specified order.
     *
     * @param orderId the ULID order identifier
     * @return list of SLA timer entities for the order
     */
    public List<SlaTimerEntity> getTimers(String orderId) {
        return slaTimerRepository.findByOrderId(orderId);
    }
}
