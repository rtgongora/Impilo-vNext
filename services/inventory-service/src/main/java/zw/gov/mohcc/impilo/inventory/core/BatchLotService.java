package zw.gov.mohcc.impilo.inventory.core;

import zw.gov.mohcc.impilo.inventory.domain.BatchLotStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.BatchLotEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dura batch/lot service: registration, lookup, near-expiry queries, and
 * governance status transitions (quarantine / recall / expire / release).
 *
 * <p>All operations are tenant-scoped via the trust context.</p>
 */
public interface BatchLotService {

    /**
     * Register (upsert) a batch/lot for a commodity. If a batch with the same
     * number already exists for the item, its mutable fields are updated.
     */
    BatchLotEntity registerBatch(String itemCode, String batchNumber, String lotNumber,
                                 LocalDate expiryDate, LocalDate manufactureDate,
                                 String manufacturer, String supplier, String serialNumber, String notes);

    /**
     * List batches for an item, earliest expiry first (FEFO order).
     */
    List<BatchLotEntity> listBatchesForItem(String itemCode);

    /**
     * Get a batch by id (tenant-checked).
     */
    BatchLotEntity getBatch(UUID batchLotId);

    /**
     * Transition a batch's governance status (e.g. quarantine, recall, expire,
     * return to active). The optional reason is recorded on the batch notes.
     */
    BatchLotEntity setStatus(UUID batchLotId, BatchLotStatus status, String reason);

    /**
     * Active batches expiring within {@code daysThreshold} days from today.
     */
    List<BatchLotEntity> nearExpiry(int daysThreshold);

    /**
     * Whether a batch is usable (ACTIVE and not past expiry).
     */
    boolean isUsable(UUID batchLotId);
}
