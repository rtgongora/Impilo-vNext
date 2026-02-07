package zw.gov.mohcc.impilo.inventory.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.inventory.persistence.entity.HandoverEntity;

import java.util.UUID;

/**
 * Service managing custody handover workflows for store shifts.
 *
 * <p>When responsibility for a store transfers between actors (e.g.
 * shift change), a handover is created and progresses through:
 * INITIATED -> OUTGOING_SIGNED -> INCOMING_SIGNED -> COMPLETED.</p>
 *
 * <p>The incoming signature automatically completes the handover,
 * recording the custody transfer for audit purposes.</p>
 */
public interface HandoverService {

    /**
     * Initiate a new custody handover for a store.
     *
     * @param storeId the store being handed over
     * @param toActor the actor receiving custody
     * @return the created handover entity in INITIATED status
     */
    HandoverEntity startHandover(UUID storeId, String toActor);

    /**
     * Record the outgoing actor's signature on the handover.
     *
     * @param handoverId the handover identifier
     * @return the updated handover in OUTGOING_SIGNED status
     * @throws IllegalStateException if handover is not in INITIATED status
     */
    HandoverEntity signOutgoing(UUID handoverId);

    /**
     * Record the incoming actor's signature, completing the handover.
     *
     * @param handoverId the handover identifier
     * @return the completed handover in COMPLETED status
     * @throws IllegalStateException if handover is not in OUTGOING_SIGNED status
     */
    HandoverEntity signIncoming(UUID handoverId);

    /**
     * List handovers for the current tenant with pagination.
     *
     * @param pageable pagination and sorting parameters
     * @return a page of handover entities
     */
    Page<HandoverEntity> getHandovers(Pageable pageable);
}
