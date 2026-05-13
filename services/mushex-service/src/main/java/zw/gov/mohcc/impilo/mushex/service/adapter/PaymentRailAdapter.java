package zw.gov.mohcc.impilo.mushex.service.adapter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * SPI for pluggable payment rail adapters.
 * Each adapter wraps a single external payment channel (mobile money, bank, card, etc.)
 * and exposes a uniform interface for the MUSHEX payment engine.
 */
public interface PaymentRailAdapter {

    /**
     * @return the adapter type key that this adapter handles (e.g. "MOBILE_MONEY", "BANK_TRANSFER")
     */
    String adapterType();

    /**
     * Whether this adapter is a real provider integration (true) or a stub / non-production
     * implementation (false). Default is {@code false} — implementations that actually move
     * money via an external provider must override this to return {@code true}.
     *
     * <p>This is the third leg of the attempt-time safety gate
     * (see {@code docs/design/phase-3-attempt-time-rail-enforcement-implementation.md} §6):
     * the {@code PaymentAttemptService} refuses to invoke {@link #initiatePayment(String,
     * java.math.BigDecimal, String, java.util.Map)} on any real-money rail unless
     * <em>both</em> {@code mushex.adapters.&lt;rail&gt;.{enabled,credentials-configured}} are
     * true <em>and</em> this method returns {@code true}. The {@code SANDBOX} adapter is
     * exempt from the gate entirely.
     *
     * <p>Why default false: every adapter implementation in this repository today is a stub
     * that logs and returns {@code PENDING} with a synthetic reference. Until a real provider
     * client lands, no adapter should announce itself as live, even if an operator
     * accidentally flips the two configuration flags on. Once a real provider client is wired
     * in for an adapter, override this method to return {@code true} in the same change-set
     * that introduces the provider client and the credential-loading.
     */
    default boolean liveCapable() {
        return false;
    }

    /**
     * Initiate a payment via this rail.
     *
     * @param intentId the MUSHEX payment intent ID
     * @param amount   the payment amount
     * @param currency ISO 4217 currency code
     * @param config   adapter-specific configuration (endpoint URLs, credentials, etc.)
     * @return adapter response with external reference and status
     */
    AdapterResponse initiatePayment(String intentId, BigDecimal amount, String currency, Map<String, String> config);

    /**
     * Check the status of a previously initiated payment.
     *
     * @param adapterRef the external reference returned from {@link #initiatePayment}
     * @param config     adapter-specific configuration
     * @return current status from the external payment system
     */
    AdapterResponse checkStatus(String adapterRef, Map<String, String> config);

    /**
     * Verify an inbound webhook signature from the payment provider.
     *
     * @param signature the signature header value
     * @param payload   the raw webhook body
     * @param config    adapter-specific configuration (e.g. webhook secret)
     * @return true if signature is valid
     */
    boolean verifyWebhook(String signature, String payload, Map<String, String> config);

    /**
     * Initiate a refund via this rail.
     *
     * @param adapterRef the original payment's external reference
     * @param amount     the refund amount
     * @param config     adapter-specific configuration
     * @return adapter response with refund reference and status
     */
    AdapterResponse initiateRefund(String adapterRef, BigDecimal amount, Map<String, String> config);
}
