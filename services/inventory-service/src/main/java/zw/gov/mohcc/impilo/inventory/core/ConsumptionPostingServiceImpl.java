package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.LedgerEventType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.LedgerEventEntity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Consumption posting service implementation.
 *
 * <p>Provides typed entry points for recording stock consumption from
 * downstream clinical systems. All methods delegate to
 * {@link LedgerService#postEvent} with an ISSUE event type and
 * appropriate reference metadata.</p>
 *
 * <p>Consumption quantities are always recorded as negative deltas
 * (stock leaving the store) in the ledger.</p>
 */
@Service
public class ConsumptionPostingServiceImpl implements ConsumptionPostingService {

    private static final Logger log = LoggerFactory.getLogger(ConsumptionPostingServiceImpl.class);

    private final LedgerService ledgerService;

    public ConsumptionPostingServiceImpl(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Posts an ISSUE event with refType=PHARMACY and refId=orderId.
     * The batch and expiry are recorded for FEFO tracking accuracy.</p>
     */
    @Override
    @Transactional
    public LedgerEventEntity postPharmacyConsumption(UUID facilityId, UUID storeId,
                                                       String itemCode, String batch,
                                                       LocalDate expiry, int qty,
                                                       String orderId) {
        validateConsumptionInput(facilityId, storeId, itemCode, qty);

        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required for pharmacy consumption");
        }

        String idempotencyKey = "PHARMACY:" + orderId + ":" + itemCode + ":" + batch;

        LedgerService.LedgerEventData data = new LedgerService.LedgerEventData(
                facilityId,
                storeId,
                null, // binId resolved by FEFO if needed
                itemCode,
                batch,
                expiry,
                -Math.abs(qty), // ensure negative delta for consumption
                null,
                LedgerEventType.ISSUE,
                "Pharmacy dispensing",
                "PHARMACY",
                orderId,
                idempotencyKey
        );

        LedgerEventEntity event = ledgerService.postEvent(data);

        log.info("Pharmacy consumption posted: facility={}, store={}, item={}, batch={}, qty={}, orderId={}",
                facilityId, storeId, itemCode, batch, qty, orderId);
        return event;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Posts an ISSUE event with refType=LAB and refId=orderId.
     * Batch and expiry are not tracked for lab consumables.</p>
     */
    @Override
    @Transactional
    public LedgerEventEntity postLabConsumption(UUID facilityId, UUID storeId,
                                                  String itemCode, int qty,
                                                  String orderId) {
        validateConsumptionInput(facilityId, storeId, itemCode, qty);

        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required for lab consumption");
        }

        String idempotencyKey = "LAB:" + orderId + ":" + itemCode;

        LedgerService.LedgerEventData data = new LedgerService.LedgerEventData(
                facilityId,
                storeId,
                null,
                itemCode,
                null, // batch not typically tracked for lab consumables
                null, // expiry not typically tracked for lab consumables
                -Math.abs(qty), // ensure negative delta for consumption
                null,
                LedgerEventType.ISSUE,
                "Lab consumption",
                "LAB",
                orderId,
                idempotencyKey
        );

        LedgerEventEntity event = ledgerService.postEvent(data);

        log.info("Lab consumption posted: facility={}, store={}, item={}, qty={}, orderId={}",
                facilityId, storeId, itemCode, qty, orderId);
        return event;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Posts an ISSUE event with the caller-provided refType and refId.
     * Suitable for ward consumption, theatre usage, procedure consumption,
     * and other clinical contexts.</p>
     */
    @Override
    @Transactional
    public LedgerEventEntity postClinicalConsumption(UUID facilityId, UUID storeId,
                                                       String itemCode, int qty,
                                                       String refType, String refId) {
        validateConsumptionInput(facilityId, storeId, itemCode, qty);

        if (refType == null || refType.isBlank()) {
            throw new IllegalArgumentException("refType is required for clinical consumption");
        }
        if (refId == null || refId.isBlank()) {
            throw new IllegalArgumentException("refId is required for clinical consumption");
        }

        String idempotencyKey = "CLINICAL:" + refType + ":" + refId + ":" + itemCode;

        LedgerService.LedgerEventData data = new LedgerService.LedgerEventData(
                facilityId,
                storeId,
                null,
                itemCode,
                null,
                null,
                -Math.abs(qty), // ensure negative delta for consumption
                null,
                LedgerEventType.ISSUE,
                "Clinical consumption (" + refType + ")",
                refType,
                refId,
                idempotencyKey
        );

        LedgerEventEntity event = ledgerService.postEvent(data);

        log.info("Clinical consumption posted: facility={}, store={}, item={}, qty={}, ref={}/{}",
                facilityId, storeId, itemCode, qty, refType, refId);
        return event;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Validate common consumption input parameters.
     */
    private void validateConsumptionInput(UUID facilityId, UUID storeId, String itemCode, int qty) {
        if (facilityId == null) {
            throw new IllegalArgumentException("facilityId is required");
        }
        if (storeId == null) {
            throw new IllegalArgumentException("storeId is required");
        }
        if (itemCode == null || itemCode.isBlank()) {
            throw new IllegalArgumentException("itemCode is required");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("Consumption quantity must be positive, got: " + qty);
        }
    }
}
