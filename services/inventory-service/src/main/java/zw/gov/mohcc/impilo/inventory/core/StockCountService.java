package zw.gov.mohcc.impilo.inventory.core;

import zw.gov.mohcc.impilo.inventory.persistence.entity.CountLineEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CountSessionEntity;

import java.util.UUID;

/**
 * Service managing physical stock count sessions and variance resolution.
 *
 * <p>Implements a state machine for count sessions:
 * DRAFT -> IN_PROGRESS -> SUBMITTED -> APPROVED / REJECTED.
 * REJECTED sessions can be reopened to IN_PROGRESS for recounting.</p>
 *
 * <p>On approval, variance adjustments are posted to the ledger as
 * COUNT_ADJUST events, bringing the on-hand projection in line with
 * the physical count.</p>
 */
public interface StockCountService {

    /**
     * Create a new count session in DRAFT status, pre-populating count lines
     * from the current on-hand positions for the specified store and optional bin.
     *
     * @param storeId the store to count
     * @param binId   the bin within the store (nullable for whole-store count)
     * @return the created session with populated lines
     */
    CountSessionEntity createSession(UUID storeId, UUID binId);

    /**
     * Transition a session from DRAFT to IN_PROGRESS.
     *
     * @param sessionId the session identifier
     * @return the updated session
     * @throws IllegalStateException if session is not in DRAFT status
     */
    CountSessionEntity startCounting(UUID sessionId);

    /**
     * Update a count line with the physically counted quantity.
     *
     * @param sessionId the session identifier
     * @param lineId    the line identifier
     * @param qtyCounted the physically counted quantity
     * @param barcode    the scanned barcode (optional verification)
     * @return the updated count line with calculated variance
     * @throws IllegalStateException if session is not in IN_PROGRESS status
     */
    CountLineEntity updateLine(UUID sessionId, UUID lineId, int qtyCounted, String barcode);

    /**
     * Submit the count for review. Transitions IN_PROGRESS to SUBMITTED
     * and finalizes all variance calculations.
     *
     * @param sessionId the session identifier
     * @return the submitted session
     * @throws IllegalStateException if session is not in IN_PROGRESS status
     */
    CountSessionEntity submitCount(UUID sessionId);

    /**
     * Approve the submitted count. Posts COUNT_ADJUST ledger events for
     * all lines with non-zero variance and transitions to APPROVED.
     *
     * @param sessionId the session identifier
     * @param notes     approval notes
     * @return the approved session
     * @throws IllegalStateException if session is not in SUBMITTED status
     */
    CountSessionEntity approveCount(UUID sessionId, String notes);

    /**
     * Reject the submitted count. Transitions to REJECTED.
     * The session can later be reopened for recounting.
     *
     * @param sessionId the session identifier
     * @param notes     rejection reason
     * @return the rejected session
     * @throws IllegalStateException if session is not in SUBMITTED status
     */
    CountSessionEntity rejectCount(UUID sessionId, String notes);

    /**
     * Retrieve a count session with its lines.
     *
     * @param sessionId the session identifier
     * @return the session entity
     * @throws IllegalArgumentException if session not found
     */
    CountSessionEntity getSession(UUID sessionId);
}
