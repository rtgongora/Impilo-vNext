package zw.gov.mohcc.impilo.inventory.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.LedgerEventType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.LedgerEventEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.OnHandEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.LedgerEventRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.OnHandRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Core append-only ledger engine implementation.
 *
 * <p>Every stock change is recorded as an immutable ledger event. The on-hand
 * projection is updated atomically within the same transaction to maintain
 * consistency. Idempotency keys prevent duplicate postings from retried
 * operations.</p>
 *
 * <p>An outbox event is written for each ledger entry to enable reliable
 * downstream event publishing via the outbox pattern.</p>
 */
@Service
public class LedgerServiceImpl implements LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerServiceImpl.class);

    private final LedgerEventRepository ledgerEventRepository;
    private final OnHandRepository onHandRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public LedgerServiceImpl(LedgerEventRepository ledgerEventRepository,
                              OnHandRepository onHandRepository,
                              EventOutboxRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.ledgerEventRepository = ledgerEventRepository;
        this.onHandRepository = onHandRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation steps:
     * <ol>
     *   <li>Check idempotency key — return existing event if duplicate</li>
     *   <li>Extract tenant and actor from TrustContext</li>
     *   <li>Create and persist the LedgerEventEntity</li>
     *   <li>Update the OnHand projection (upsert)</li>
     *   <li>Write an outbox event for downstream consumers</li>
     * </ol>
     */
    @Override
    @Transactional
    public LedgerEventEntity postEvent(LedgerEventData data) {
        // 1. Idempotency check — if an event with this key exists, return it
        if (data.idempotencyKey() != null && !data.idempotencyKey().isBlank()) {
            Optional<LedgerEventEntity> existing = ledgerEventRepository.findByIdempotencyKey(data.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotent duplicate detected for key={}, returning existing eventId={}",
                        data.idempotencyKey(), existing.get().getEventId());
                return existing.get();
            }
        }

        // 2. Get trust context for tenantId and actorId
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        // 3. Validate inputs
        if (data.facilityId() == null) {
            throw new IllegalArgumentException("facilityId is required");
        }
        if (data.storeId() == null) {
            throw new IllegalArgumentException("storeId is required");
        }
        if (data.itemCode() == null || data.itemCode().isBlank()) {
            throw new IllegalArgumentException("itemCode is required");
        }
        if (data.eventType() == null) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (data.qtyDelta() == 0) {
            throw new IllegalArgumentException("qtyDelta must not be zero");
        }

        // 4. Create and persist the ledger event
        LedgerEventEntity event = new LedgerEventEntity();
        event.setTenantId(tenantId);
        event.setFacilityId(data.facilityId());
        event.setStoreId(data.storeId());
        event.setBinId(data.binId());
        event.setItemCode(data.itemCode());
        event.setBatch(data.batch());
        event.setExpiry(data.expiry());
        event.setQtyDelta(data.qtyDelta());
        event.setUom(data.uom());
        event.setEventType(data.eventType());
        event.setReason(data.reason());
        event.setRefType(data.refType());
        event.setRefId(data.refId());
        event.setIdempotencyKey(data.idempotencyKey());
        event.setActorId(actorId);

        event = ledgerEventRepository.save(event);

        log.info("Ledger event created: eventId={}, facility={}, store={}, item={}, qty={}, type={}, ref={}/{}",
                event.getEventId(), data.facilityId(), data.storeId(), data.itemCode(),
                data.qtyDelta(), data.eventType(), data.refType(), data.refId());

        // 5. Update on-hand projection
        updateOnHandProjection(tenantId, data);

        // 6. Write outbox event for downstream consumers
        publishOutboxEvent("LEDGER_EVENT", event.getEventId().toString(),
                "LEDGER_EVENT_CREATED", event, tenantId);

        return event;
    }

    @Override
    public Page<LedgerEventEntity> getLedgerEvents(UUID facilityId, UUID storeId,
                                                     String itemCode, Pageable pageable) {
        TrustContext ctx = TrustContextHolder.require();

        if (itemCode != null && !itemCode.isBlank()) {
            return ledgerEventRepository.findByTenantIdAndFacilityIdAndStoreIdAndItemCode(
                    ctx.tenantId(), facilityId, storeId, itemCode, pageable);
        }
        return ledgerEventRepository.findByTenantIdAndFacilityIdAndStoreId(
                ctx.tenantId(), facilityId, storeId, pageable);
    }

    @Override
    public int getBalance(UUID facilityId, UUID storeId, String itemCode) {
        return ledgerEventRepository.getStockBalance(facilityId, storeId, itemCode);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Update the on-hand projection by upserting the on-hand row for the
     * given facility/store/item/batch/expiry combination.
     *
     * <p>If an existing on-hand row is found, its quantity is incremented by
     * the delta. If no row exists, a new one is created with the delta as
     * the initial quantity.</p>
     */
    private void updateOnHandProjection(UUID tenantId, LedgerEventData data) {
        Optional<OnHandEntity> existingOpt = onHandRepository
                .findByFacilityIdAndStoreIdAndItemCodeAndBatchAndExpiry(
                        data.facilityId(), data.storeId(), data.itemCode(),
                        data.batch(), data.expiry());

        if (existingOpt.isPresent()) {
            OnHandEntity existing = existingOpt.get();
            int newQty = existing.getQtyOnHand() + data.qtyDelta();
            existing.setQtyOnHand(newQty);
            existing.setUpdatedAt(OffsetDateTime.now());

            // Update bin if provided and not already set
            if (data.binId() != null && existing.getBinId() == null) {
                existing.setBinId(data.binId());
            }

            onHandRepository.save(existing);
            log.debug("On-hand updated: facility={}, store={}, item={}, batch={}, newQty={}",
                    data.facilityId(), data.storeId(), data.itemCode(), data.batch(), newQty);
        } else {
            OnHandEntity newOnHand = new OnHandEntity();
            newOnHand.setTenantId(tenantId);
            newOnHand.setFacilityId(data.facilityId());
            newOnHand.setStoreId(data.storeId());
            newOnHand.setBinId(data.binId());
            newOnHand.setItemCode(data.itemCode());
            newOnHand.setBatch(data.batch());
            newOnHand.setExpiry(data.expiry());
            newOnHand.setQtyOnHand(data.qtyDelta());
            newOnHand.setUpdatedAt(OffsetDateTime.now());

            onHandRepository.save(newOnHand);
            log.debug("On-hand created: facility={}, store={}, item={}, batch={}, qty={}",
                    data.facilityId(), data.storeId(), data.itemCode(), data.batch(), data.qtyDelta());
        }
    }

    /**
     * Write an event to the transactional outbox for reliable Kafka publishing.
     */
    private void publishOutboxEvent(String aggregateType, String aggregateId,
                                     String eventType, Object payload, UUID tenantId) {
        try {
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType(aggregateType);
            outbox.setAggregateId(aggregateId);
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(tenantId);
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write outbox event: type={}, aggregateId={}", eventType, aggregateId, e);
        }
    }

    private void publishStockLevelTelemetrySnapshot(UUID tenantId, LedgerEventEntity event) {
        try {
            Optional<OnHandEntity> onHandOpt = onHandRepository
                    .findByFacilityIdAndStoreIdAndItemCodeAndBatchAndExpiry(
                            event.getFacilityId(), event.getStoreId(), event.getItemCode(),
                            event.getBatch(), event.getExpiry());
            int qtyOnHand = onHandOpt.map(OnHandEntity::getQtyOnHand).orElse(0);
            UUID binId = onHandOpt.map(OnHandEntity::getBinId).orElse(event.getBinId());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tenant_id", tenantId.toString());
            payload.put("facility_id", event.getFacilityId().toString());
            payload.put("store_id", event.getStoreId().toString());
            payload.put("bin_id", binId != null ? binId.toString() : null);
            payload.put("item_code", event.getItemCode());
            payload.put("batch", event.getBatch());
            payload.put("expiry", event.getExpiry() != null ? event.getExpiry().toString() : null);
            payload.put("qty_on_hand", qtyOnHand);
            payload.put("ledger_event_id", event.getEventId().toString());
            payload.put("as_of", OffsetDateTime.now().toString());

            publishOutboxEvent("ON_HAND", onHandKey(event), "INVENTORY_STOCK_LEVEL_TELEMETRY", payload, tenantId);
        } catch (Exception e) {
            log.warn("Failed to write stock level telemetry outbox for event {}: {}", event.getEventId(), e.getMessage());
        }
    }

    private static String onHandKey(LedgerEventEntity event) {
        return event.getFacilityId() + ":" + event.getStoreId() + ":" + event.getItemCode();
    }
}
