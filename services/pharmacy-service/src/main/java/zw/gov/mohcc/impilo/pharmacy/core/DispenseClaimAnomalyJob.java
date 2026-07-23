package zw.gov.mohcc.impilo.pharmacy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseOrderRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OF-B12 — the §13.3.1 claim-to-dispense cross-matching sweep (pharmacy side).
 *
 * <p>Two coded anomalies, each emitted exactly once per episode
 * (emit-once marker {@code claim_anomaly_flagged_at}):</p>
 * <ul>
 *   <li><b>UNBOUND_DISPENSE</b> — a completed dispense episode for an OROS
 *       medication-profile order that carries no prescription claim
 *       (defeats T10: dispense without a claim).</li>
 *   <li><b>CLAIM_EPISODE_TTL_EXPIRED</b> — an episode carrying a claim that
 *       never reached a dispensed state within the claim TTL (the pharmacy-side
 *       view of "claim with no episode within TTL"; the OROS-side view of a
 *       claim whose order event never reached pharmacy at all is an OROS-lane
 *       sweep and is deliberately NOT duplicated here).</li>
 * </ul>
 *
 * <p>Anomalies are events, never blocks: the dual-run window with legacy
 * claim-less flows (OD-13) makes hard-blocking dishonest today.</p>
 */
@Service
public class DispenseClaimAnomalyJob {

    private static final Logger log = LoggerFactory.getLogger(DispenseClaimAnomalyJob.class);

    private final DispenseOrderRepository orderRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final int claimEpisodeTtlHours;
    private final int unboundGraceMinutes;

    public DispenseClaimAnomalyJob(DispenseOrderRepository orderRepository,
                                   EventOutboxRepository outboxRepository,
                                   ObjectMapper objectMapper,
                                   @Value("${pharmacy.claims.episode-ttl-hours:72}") int claimEpisodeTtlHours,
                                   @Value("${pharmacy.claims.unbound-grace-minutes:30}") int unboundGraceMinutes) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.claimEpisodeTtlHours = claimEpisodeTtlHours;
        this.unboundGraceMinutes = unboundGraceMinutes;
    }

    @Scheduled(fixedDelayString = "${pharmacy.claims.anomaly-sweep-interval-ms:300000}")
    @Transactional
    public void sweep() {
        OffsetDateTime now = OffsetDateTime.now();

        List<DispenseOrderEntity> unbound =
                orderRepository.findUnboundDispenseAnomalies(now.minusMinutes(unboundGraceMinutes));
        for (DispenseOrderEntity order : unbound) {
            emitAnomaly(order, "UNBOUND_DISPENSE",
                    "Completed dispense episode for OROS order " + order.getOrosOrderId()
                            + " has no prescription claim (§13.3.1)");
        }

        List<DispenseOrderEntity> stalled =
                orderRepository.findStalledClaimEpisodes(now.minusHours(claimEpisodeTtlHours));
        for (DispenseOrderEntity order : stalled) {
            emitAnomaly(order, "CLAIM_EPISODE_TTL_EXPIRED",
                    "Episode carrying prescription claim " + order.getPrescriptionClaimId()
                            + " has not dispensed within " + claimEpisodeTtlHours + "h (§13.3.1)");
        }

        if (!unbound.isEmpty() || !stalled.isEmpty()) {
            log.info("Claim-linkage anomaly sweep: {} unbound dispenses, {} stalled claim episodes",
                    unbound.size(), stalled.size());
        }
    }

    private void emitAnomaly(DispenseOrderEntity order, String code, String detail) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("code", code);
            payload.put("orderId", order.getOrderId().toString());
            payload.put("orosOrderId", order.getOrosOrderId());
            payload.put("prescriptionClaimId", order.getPrescriptionClaimId() != null
                    ? order.getPrescriptionClaimId().toString() : null);
            payload.put("status", order.getStatus() != null ? order.getStatus().name() : null);
            payload.put("detail", detail);

            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("DISPENSE_ORDER");
            event.setAggregateId(order.getOrderId().toString());
            event.setEventType("DISPENSE_CLAIM_ANOMALY");
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(order.getTenantId());
            outboxRepository.save(event);

            order.setClaimAnomalyFlaggedAt(OffsetDateTime.now());
            orderRepository.save(order);

            log.warn("Claim-linkage anomaly {}: orderId={}, detail={}",
                    code, order.getOrderId(), detail);
        } catch (Exception e) {
            log.error("Failed to emit claim anomaly {} for order {}: {}",
                    code, order.getOrderId(), e.getMessage(), e);
        }
    }
}
