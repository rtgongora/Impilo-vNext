package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.BatchLotStatus;
import zw.gov.mohcc.impilo.inventory.domain.RecallSeverity;
import zw.gov.mohcc.impilo.inventory.domain.RecallSource;
import zw.gov.mohcc.impilo.inventory.domain.RecallStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.BatchLotEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RecallEventEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.RecallEventRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of the Dura recall service.
 */
@Service
public class RecallServiceImpl implements RecallService {

    private static final Logger log = LoggerFactory.getLogger(RecallServiceImpl.class);

    private final RecallEventRepository recallRepository;
    private final BatchLotService batchLotService;

    public RecallServiceImpl(RecallEventRepository recallRepository, BatchLotService batchLotService) {
        this.recallRepository = recallRepository;
        this.batchLotService = batchLotService;
    }

    @Override
    @Transactional
    public RecallEventEntity createRecall(String recallReference, String itemCode, String batchNumber,
                                          RecallSource source, RecallSeverity severity, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        if (itemCode == null || itemCode.isBlank()) {
            throw new IllegalArgumentException("itemCode is required");
        }

        int frozen = 0;
        for (BatchLotEntity batch : matchingBatches(itemCode, batchNumber)) {
            if (batch.getStatus() != BatchLotStatus.RECALLED) {
                batchLotService.setStatus(batch.getBatchLotId(), BatchLotStatus.RECALLED,
                        "Recall" + (recallReference != null ? " " + recallReference : ""));
            }
            frozen++;
        }

        RecallEventEntity recall = new RecallEventEntity();
        recall.setTenantId(ctx.tenantId());
        recall.setRecallReference(recallReference);
        recall.setItemCode(itemCode);
        recall.setBatchNumber(batchNumber);
        recall.setSource(source != null ? source : RecallSource.INTERNAL);
        recall.setSeverity(severity != null ? severity : RecallSeverity.MEDIUM);
        recall.setReason(reason);
        recall.setStatus(RecallStatus.OPEN);
        recall.setAffectedBatches(frozen);
        recall.setInitiatedBy(ctx.actorId());

        recall = recallRepository.save(recall);
        log.info("Recall opened: ref={}, itemCode={}, batch={}, frozenBatches={}, severity={}",
                recallReference, itemCode, batchNumber, frozen, recall.getSeverity());
        return recall;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecallEventEntity> listRecalls(RecallStatus status) {
        TrustContext ctx = TrustContextHolder.require();
        if (status != null) {
            return recallRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(ctx.tenantId(), status);
        }
        return recallRepository.findByTenantIdOrderByCreatedAtDesc(ctx.tenantId());
    }

    @Override
    @Transactional(readOnly = true)
    public RecallEventEntity getRecall(UUID recallId) {
        return loadOwned(recallId);
    }

    @Override
    @Transactional
    public RecallEventEntity closeRecall(UUID recallId, String closureNote) {
        RecallEventEntity recall = loadOwned(recallId);
        if (recall.getStatus() == RecallStatus.CLOSED) {
            throw new IllegalStateException("Recall already closed: " + recallId);
        }
        recall.setStatus(RecallStatus.CLOSED);
        recall.setClosureNote(closureNote);
        recall.setClosedAt(OffsetDateTime.now());
        recall = recallRepository.save(recall);
        log.info("Recall closed: recallId={}", recallId);
        return recall;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BatchLotEntity> affectedBatches(UUID recallId) {
        RecallEventEntity recall = loadOwned(recallId);
        List<BatchLotEntity> result = new ArrayList<>();
        for (BatchLotEntity batch : matchingBatches(recall.getItemCode(), recall.getBatchNumber())) {
            if (batch.getStatus() == BatchLotStatus.RECALLED) {
                result.add(batch);
            }
        }
        return result;
    }

    /**
     * Batches of an item, optionally narrowed to a single batch number.
     */
    private List<BatchLotEntity> matchingBatches(String itemCode, String batchNumber) {
        List<BatchLotEntity> batches = batchLotService.listBatchesForItem(itemCode);
        if (batchNumber == null || batchNumber.isBlank()) {
            return batches;
        }
        List<BatchLotEntity> filtered = new ArrayList<>();
        for (BatchLotEntity b : batches) {
            if (batchNumber.equals(b.getBatchNumber())) {
                filtered.add(b);
            }
        }
        return filtered;
    }

    private RecallEventEntity loadOwned(UUID recallId) {
        TrustContext ctx = TrustContextHolder.require();
        RecallEventEntity recall = recallRepository.findById(recallId)
                .orElseThrow(() -> new IllegalArgumentException("Recall not found: " + recallId));
        if (recall.getTenantId() == null || !recall.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Recall does not belong to the current tenant");
        }
        return recall;
    }
}
