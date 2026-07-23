package zw.gov.mohcc.impilo.inventory.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.ReservationStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.OnHandEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.StockReservationEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.StockReservationRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of the Dura stock reservation service.
 *
 * <p>OF-B11: every reservation lifecycle change is evented through the outbox —
 * a typed {@code inventory.reservation.*.v1} event plus the legacy
 * {@code inventory.reservation.status_changed} shape
 * ({@code {reservationRef, status}}) that the msika-flow projection consumer
 * parses. The scheduled expiry sweep ({@link #expireLapsed}) flips lapsed
 * ACTIVE holds to EXPIRED so no zombie hold outlives its TTL (§8.9.3).</p>
 */
@Service
public class ReservationServiceImpl implements ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationServiceImpl.class);
    private static final int ON_HAND_PAGE_SIZE = 10_000;

    private final StockReservationRepository reservationRepository;
    private final OnHandService onHandService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ReservationServiceImpl(StockReservationRepository reservationRepository,
                                  OnHandService onHandService,
                                  EventOutboxRepository outboxRepository,
                                  ObjectMapper objectMapper) {
        this.reservationRepository = reservationRepository;
        this.onHandService = onHandService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public StockReservationEntity reserve(UUID facilityId, UUID storeId, String itemCode, UUID batchLotId,
                                          int qty, String uom, String refType, String refId,
                                          String reason, OffsetDateTime expiresAt) {
        TrustContext ctx = TrustContextHolder.require();
        if (facilityId == null || storeId == null) {
            throw new IllegalArgumentException("facilityId and storeId are required");
        }
        if (itemCode == null || itemCode.isBlank()) {
            throw new IllegalArgumentException("itemCode is required");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }

        int available = availableQuantity(facilityId, storeId, itemCode);
        if (available < qty) {
            throw new IllegalStateException(
                    "Insufficient available stock for " + itemCode + ": requested " + qty + ", available " + available);
        }

        StockReservationEntity reservation = new StockReservationEntity();
        reservation.setTenantId(ctx.tenantId());
        reservation.setFacilityId(facilityId);
        reservation.setStoreId(storeId);
        reservation.setItemCode(itemCode);
        reservation.setBatchLotId(batchLotId);
        reservation.setQty(qty);
        reservation.setUom(uom);
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setRefType(refType);
        reservation.setRefId(refId);
        reservation.setReservedBy(ctx.actorId());
        reservation.setReason(reason);
        reservation.setExpiresAt(expiresAt);

        reservation = reservationRepository.save(reservation);
        emitReservationEvents(reservation, "RESERVATION_CREATED");
        log.info("Stock reserved: itemCode={}, qty={}, refType={}, refId={}", itemCode, qty, refType, refId);
        return reservation;
    }

    @Override
    @Transactional
    public StockReservationEntity release(UUID reservationId) {
        return transition(reservationId, ReservationStatus.RELEASED, "RESERVATION_RELEASED");
    }

    @Override
    @Transactional
    public StockReservationEntity consume(UUID reservationId) {
        return transition(reservationId, ReservationStatus.CONSUMED, "RESERVATION_CONSUMED");
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockReservationEntity> listActive(UUID facilityId, UUID storeId, String itemCode) {
        TrustContext ctx = TrustContextHolder.require();
        return reservationRepository.findByTenantIdAndFacilityIdAndStoreIdAndItemCodeAndStatus(
                ctx.tenantId(), facilityId, storeId, itemCode, ReservationStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public int reservedQuantity(UUID facilityId, UUID storeId, String itemCode) {
        TrustContext ctx = TrustContextHolder.require();
        return reservationRepository.sumActiveReserved(ctx.tenantId(), facilityId, storeId, itemCode);
    }

    @Override
    @Transactional(readOnly = true)
    public int availableQuantity(UUID facilityId, UUID storeId, String itemCode) {
        int onHand = onHandService
                .getOnHand(facilityId, storeId, null, itemCode, PageRequest.of(0, ON_HAND_PAGE_SIZE))
                .getContent().stream()
                .mapToInt(OnHandEntity::getQtyOnHand)
                .sum();
        int reserved = reservedQuantity(facilityId, storeId, itemCode);
        return onHand - reserved;
    }

    /**
     * System sweep (no trust context — scheduled): flips every ACTIVE
     * reservation whose {@code expiresAt} has lapsed to EXPIRED and events the
     * expiry (fail-close §8.9.3 — no zombie holds).
     */
    @Override
    @Transactional
    public int expireLapsed(OffsetDateTime now) {
        List<StockReservationEntity> lapsed = reservationRepository
                .findByStatusAndExpiresAtIsNotNullAndExpiresAtBefore(ReservationStatus.ACTIVE, now);
        for (StockReservationEntity reservation : lapsed) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);
            emitReservationEvents(reservation, "RESERVATION_EXPIRED");
            log.info("Reservation {} EXPIRED (ttl {} lapsed): item={}, qty={}, ref={}:{}",
                    reservation.getReservationId(), reservation.getExpiresAt(),
                    reservation.getItemCode(), reservation.getQty(),
                    reservation.getRefType(), reservation.getRefId());
        }
        return lapsed.size();
    }

    private StockReservationEntity transition(UUID reservationId, ReservationStatus target, String eventType) {
        TrustContext ctx = TrustContextHolder.require();
        StockReservationEntity reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));
        if (reservation.getTenantId() == null || !reservation.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Reservation does not belong to the current tenant");
        }
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Reservation " + reservationId + " is not ACTIVE (was " + reservation.getStatus() + ")");
        }
        reservation.setStatus(target);
        reservation = reservationRepository.save(reservation);
        emitReservationEvents(reservation, eventType);
        log.info("Reservation {} -> {}", reservationId, target);
        return reservation;
    }

    /**
     * Writes BOTH outbox rows for a lifecycle change: the typed
     * {@code RESERVATION_*} event (→ {@code inventory.reservation.*.v1}) and the
     * legacy {@code RESERVATION_STATUS_CHANGED} shape
     * (→ {@code inventory.reservation.status_changed}) consumed by the
     * msika-flow projection.
     */
    private void emitReservationEvents(StockReservationEntity reservation, String eventType) {
        String reservationRef = reservation.getReservationId() != null
                ? reservation.getReservationId().toString() : null;

        Map<String, Object> typed = new LinkedHashMap<>();
        typed.put("reservationId", reservationRef);
        typed.put("reservationRef", reservationRef);
        typed.put("status", reservation.getStatus().name());
        typed.put("facilityId", reservation.getFacilityId() != null ? reservation.getFacilityId().toString() : null);
        typed.put("storeId", reservation.getStoreId() != null ? reservation.getStoreId().toString() : null);
        typed.put("itemCode", reservation.getItemCode());
        typed.put("qty", reservation.getQty());
        typed.put("uom", reservation.getUom());
        typed.put("refType", reservation.getRefType());
        typed.put("refId", reservation.getRefId());
        typed.put("expiresAt", reservation.getExpiresAt() != null ? reservation.getExpiresAt().toString() : null);
        writeOutbox("StockReservation", reservationRef, eventType, typed, reservation.getTenantId());

        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("reservationRef", reservationRef);
        legacy.put("status", reservation.getStatus().name());
        writeOutbox("StockReservation", reservationRef, "RESERVATION_STATUS_CHANGED", legacy,
                reservation.getTenantId());
    }

    private void writeOutbox(String aggregateType, String aggregateId, String eventType,
                             Map<String, Object> payload, UUID tenantId) {
        try {
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType(aggregateType);
            outbox.setAggregateId(aggregateId != null ? aggregateId : "unknown");
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(tenantId);
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write reservation outbox event {}: {}", eventType, e.getMessage());
        }
    }
}
