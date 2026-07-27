package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeliveryRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.DeliveryRecordRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Records the birth from the mother's side.
 *
 * <p>The clinical safety properties are enforced below the service by CHECK constraints — a
 * caesarean must reference its theatre episode, a third- or fourth-degree tear must record its
 * repair, suspected PPH must carry its blood-loss estimate — so they hold even against a write that
 * bypasses this service. What the service adds is the store-and-forward and single-delivery
 * behaviour that a constraint cannot express as a friendly outcome: a replayed offline packet
 * returns the existing record, and a second delivery for a pregnancy is a reconcilable conflict, not
 * a 500 that loses the birth.
 */
@Service
public class DeliveryRecordService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRecordService.class);

    private final DeliveryRecordRepository deliveries;

    public DeliveryRecordService(DeliveryRecordRepository deliveries) {
        this.deliveries = deliveries;
    }

    /** Raised when a pregnancy already has a delivery. Carries the existing record's id. */
    public static class DuplicateDeliveryException extends RuntimeException {
        private final UUID existingDeliveryRecordId;

        public DuplicateDeliveryException(UUID existingDeliveryRecordId, String message) {
            super(message);
            this.existingDeliveryRecordId = existingDeliveryRecordId;
        }

        public UUID getExistingDeliveryRecordId() {
            return existingDeliveryRecordId;
        }
    }

    @Transactional(readOnly = true)
    public Optional<DeliveryRecordEntity> forPregnancy(UUID tenantId, UUID episodeId) {
        if (tenantId == null || episodeId == null) {
            return Optional.empty();
        }
        return deliveries.findForPregnancy(tenantId, episodeId);
    }

    @Transactional(readOnly = true)
    public List<DeliveryRecordEntity> forMother(UUID tenantId, String motherCpid) {
        if (tenantId == null || motherCpid == null || motherCpid.isBlank()) {
            return List.of();
        }
        return deliveries.findByMother(tenantId, motherCpid);
    }

    /**
     * Record a birth.
     *
     * <p>A replayed offline packet returns the record it already created rather than a second birth,
     * and a second delivery for the same pregnancy raises {@link DuplicateDeliveryException} carrying
     * the existing id so the caller answers 409 with a reconciliation, not a 500 — a 500 here would
     * lose an offline record of a birth that actually happened. Twins are one delivery with
     * {@code babiesDelivered > 1}, not two delivery rows.
     */
    @Transactional
    public DeliveryRecordEntity record(DeliveryRecordEntity delivery) {
        if (delivery.getClientOfflineId() != null && !delivery.getClientOfflineId().isBlank()) {
            Optional<DeliveryRecordEntity> replayed = deliveries.findByTenantIdAndClientOfflineId(
                    delivery.getTenantId(), delivery.getClientOfflineId());
            if (replayed.isPresent()) {
                log.info("Delivery record {} replayed from offline id {}; returning the existing record",
                        replayed.get().getDeliveryRecordId(), delivery.getClientOfflineId());
                return replayed.get();
            }
        }

        if (delivery.getPregnancyEpisodeId() != null) {
            deliveries.findForPregnancy(delivery.getTenantId(), delivery.getPregnancyEpisodeId())
                    .ifPresent(existing -> {
                        throw new DuplicateDeliveryException(existing.getDeliveryRecordId(),
                                "This pregnancy already has a delivery recorded on "
                                        + existing.getDeliveredAt()
                                        + ". A second baby from the same birth is recorded as a "
                                        + "higher babiesDelivered count and a newborn record, not a "
                                        + "second delivery.");
                    });
        }

        if (delivery.getDeliveryRecordId() == null) {
            delivery.setDeliveryRecordId(UUID.randomUUID());
        }
        if (delivery.getStatus() == null) {
            delivery.setStatus(DeliveryRecordEntity.STATUS_RECORDED);
        }
        if (delivery.getRecordedAt() == null) {
            delivery.setRecordedAt(OffsetDateTime.now());
        }
        if (delivery.getPphSuspected() == null) {
            delivery.setPphSuspected(Boolean.FALSE);
        }
        return deliveries.save(delivery);
    }
}
