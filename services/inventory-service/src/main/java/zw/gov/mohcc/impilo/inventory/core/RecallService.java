package zw.gov.mohcc.impilo.inventory.core;

import zw.gov.mohcc.impilo.inventory.domain.RecallSeverity;
import zw.gov.mohcc.impilo.inventory.domain.RecallSource;
import zw.gov.mohcc.impilo.inventory.domain.RecallStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.BatchLotEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RecallEventEntity;

import java.util.List;
import java.util.UUID;

/**
 * Dura recall service.
 *
 * <p>Opening a recall for a commodity (optionally a specific batch) freezes the
 * affected batches by transitioning them to RECALLED — a blocked status that
 * prevents dispensing/administration/use. Closing records the response outcome.</p>
 *
 * <p>All operations are tenant-scoped via the trust context.</p>
 */
public interface RecallService {

    /**
     * Open a recall and freeze affected batches.
     *
     * @param recallReference external recall reference (optional)
     * @param itemCode        affected commodity (required)
     * @param batchNumber     specific batch ({@code null} = all batches of the item)
     * @param source          recall origin
     * @param severity        recall severity
     * @param reason          recall reason
     */
    RecallEventEntity createRecall(String recallReference, String itemCode, String batchNumber,
                                   RecallSource source, RecallSeverity severity, String reason);

    /**
     * List recalls, optionally filtered by status.
     */
    List<RecallEventEntity> listRecalls(RecallStatus status);

    /**
     * Get a recall by id (tenant-checked).
     */
    RecallEventEntity getRecall(UUID recallId);

    /**
     * Close a recall with an outcome note.
     */
    RecallEventEntity closeRecall(UUID recallId, String closureNote);

    /**
     * Batches frozen by this recall (RECALLED, matching the recall's item/batch).
     */
    List<BatchLotEntity> affectedBatches(UUID recallId);
}
