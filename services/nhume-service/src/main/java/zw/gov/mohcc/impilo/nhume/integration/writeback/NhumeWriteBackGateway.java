package zw.gov.mohcc.impilo.nhume.integration.writeback;

/**
 * Outbound lifecycle confirmations to the services that own the cargo truth.
 * Implementations must synthesize the mandatory v1.1 trust headers and
 * propagate the signing actor's bearer token where available.
 */
public interface NhumeWriteBackGateway {

    /** Receive all in-flight specimens of an OROS lab order at the destination lab. */
    WriteBackOutcome orosReceiveByOrder(String orosOrderRef, WriteBackContext ctx);

    /** Confirm an ISSUED MADI blood order as delivered/complete. */
    WriteBackOutcome madiCompleteOrder(String madiOrderRef, WriteBackContext ctx);

    /** Accept a submitted PCT referral package on arrival. */
    WriteBackOutcome pctAcceptReferral(String pctReferralRef, WriteBackContext ctx);

    /**
     * Report a marketplace dispatch milestone (PICKED_UP / DELIVERED) back to
     * Msika Flow, which owns the marketplace order truth.
     *
     * @param orderRef  the Msika Flow order id ({@code marketplace_order_ref} /
     *                  {@code metadata.links.msikaFlowOrderRef})
     * @param status    {@code PICKED_UP} or {@code DELIVERED}
     * @param proofRef  optional proof-of-delivery reference (nullable)
     */
    WriteBackOutcome msikaFlowDispatchStatus(String orderRef, String status, String deliveryId,
                                             String proofRef, WriteBackContext ctx);

    /**
     * OF-B17 — project a delivery-status change onto a marketplace commitment
     * SELECTION ({@code metadata.links.msikaFlowSelectionRef}). Msika Flow owns
     * the selection truth; Nhume reports the physical fact.
     *
     * @param selectionRef       the {@code mf_selections.selection_id}
     * @param status             PICKED_UP / DELIVERED / DELIVERY_ATTEMPTED /
     *                           RETURNED / FAILED
     * @param proofRef           optional proof-of-delivery reference (nullable)
     * @param verificationGrade  achieved §12.4 PoD grade for DELIVERED (nullable)
     */
    WriteBackOutcome msikaFlowSelectionDeliveryStatus(String selectionRef, String status,
                                                      String deliveryId, String proofRef,
                                                      String verificationGrade, WriteBackContext ctx);

    /** Trust/identity context carried into each outbound call. */
    record WriteBackContext(
            String tenantId,
            String podId,
            String correlationId,
            String actorId,
            String actorType,
            String bearerToken,
            String deliveryId) {
    }
}
