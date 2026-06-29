package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.BatchLotStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.BatchLotEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ItemEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.BatchLotRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ItemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of the Dura batch/lot service.
 */
@Service
public class BatchLotServiceImpl implements BatchLotService {

    private static final Logger log = LoggerFactory.getLogger(BatchLotServiceImpl.class);

    private final BatchLotRepository batchLotRepository;
    private final ItemRepository itemRepository;

    public BatchLotServiceImpl(BatchLotRepository batchLotRepository, ItemRepository itemRepository) {
        this.batchLotRepository = batchLotRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    @Transactional
    public BatchLotEntity registerBatch(String itemCode, String batchNumber, String lotNumber,
                                        LocalDate expiryDate, LocalDate manufactureDate,
                                        String manufacturer, String supplier, String serialNumber, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        if (itemCode == null || itemCode.isBlank()) {
            throw new IllegalArgumentException("itemCode is required");
        }
        if (batchNumber == null || batchNumber.isBlank()) {
            throw new IllegalArgumentException("batchNumber is required");
        }

        ItemEntity item = itemRepository.findByTenantIdAndItemCode(ctx.tenantId(), itemCode)
                .orElseThrow(() -> new IllegalArgumentException("Commodity not found: " + itemCode));

        BatchLotEntity batch = batchLotRepository
                .findByTenantIdAndItemIdAndBatchNumber(ctx.tenantId(), item.getItemId(), batchNumber)
                .orElseGet(() -> {
                    BatchLotEntity b = new BatchLotEntity();
                    b.setTenantId(ctx.tenantId());
                    b.setItemId(item.getItemId());
                    b.setItemCode(itemCode);
                    b.setBatchNumber(batchNumber);
                    b.setStatus(BatchLotStatus.ACTIVE);
                    return b;
                });

        batch.setLotNumber(lotNumber);
        batch.setExpiryDate(expiryDate);
        batch.setManufactureDate(manufactureDate);
        batch.setManufacturer(manufacturer);
        batch.setSupplier(supplier);
        batch.setSerialNumber(serialNumber);
        if (notes != null) {
            batch.setNotes(notes);
        }

        batch = batchLotRepository.save(batch);
        log.info("Batch registered: itemCode={}, batch={}, expiry={}", itemCode, batchNumber, expiryDate);
        return batch;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BatchLotEntity> listBatchesForItem(String itemCode) {
        TrustContext ctx = TrustContextHolder.require();
        return batchLotRepository.findByTenantIdAndItemCodeOrderByExpiryDateAsc(ctx.tenantId(), itemCode);
    }

    @Override
    @Transactional(readOnly = true)
    public BatchLotEntity getBatch(UUID batchLotId) {
        return loadOwned(batchLotId);
    }

    @Override
    @Transactional
    public BatchLotEntity setStatus(UUID batchLotId, BatchLotStatus status, String reason) {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        BatchLotEntity batch = loadOwned(batchLotId);
        batch.setStatus(status);
        if (reason != null && !reason.isBlank()) {
            batch.setNotes(reason);
        }
        batch = batchLotRepository.save(batch);
        log.info("Batch status set: batchLotId={}, status={}, reason={}", batchLotId, status, reason);
        return batch;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BatchLotEntity> nearExpiry(int daysThreshold) {
        TrustContext ctx = TrustContextHolder.require();
        LocalDate cutoff = LocalDate.now().plusDays(Math.max(0, daysThreshold));
        return batchLotRepository.findNearExpiry(ctx.tenantId(), BatchLotStatus.ACTIVE, cutoff);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUsable(UUID batchLotId) {
        BatchLotEntity batch = loadOwned(batchLotId);
        if (batch.getStatus() != BatchLotStatus.ACTIVE) {
            return false;
        }
        return batch.getExpiryDate() == null || !batch.getExpiryDate().isBefore(LocalDate.now());
    }

    private BatchLotEntity loadOwned(UUID batchLotId) {
        TrustContext ctx = TrustContextHolder.require();
        BatchLotEntity batch = batchLotRepository.findById(batchLotId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchLotId));
        if (batch.getTenantId() == null || !batch.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Batch does not belong to the current tenant");
        }
        return batch;
    }
}
