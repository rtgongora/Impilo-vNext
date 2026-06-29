package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.ReservationStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.OnHandEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.StockReservationEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.StockReservationRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of the Dura stock reservation service.
 */
@Service
public class ReservationServiceImpl implements ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationServiceImpl.class);
    private static final int ON_HAND_PAGE_SIZE = 10_000;

    private final StockReservationRepository reservationRepository;
    private final OnHandService onHandService;

    public ReservationServiceImpl(StockReservationRepository reservationRepository, OnHandService onHandService) {
        this.reservationRepository = reservationRepository;
        this.onHandService = onHandService;
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
        log.info("Stock reserved: itemCode={}, qty={}, refType={}, refId={}", itemCode, qty, refType, refId);
        return reservation;
    }

    @Override
    @Transactional
    public StockReservationEntity release(UUID reservationId) {
        return transition(reservationId, ReservationStatus.RELEASED);
    }

    @Override
    @Transactional
    public StockReservationEntity consume(UUID reservationId) {
        return transition(reservationId, ReservationStatus.CONSUMED);
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

    private StockReservationEntity transition(UUID reservationId, ReservationStatus target) {
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
        log.info("Reservation {} -> {}", reservationId, target);
        return reservation;
    }
}
