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
