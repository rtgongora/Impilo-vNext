package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls whether the BFF wallet path may serve an in-memory fallback when the
 * upstream Mushe Wallet service is unavailable.
 *
 * <p>Doctrine: MusheX gateway-neutrality, "Wallet local-fallback principle".
 * See {@code docs/doctrine/mushex-gateway-neutrality.md} and audit gap
 * {@code G-5} in {@code docs/audits/costa-mushex-experience-layer-wiring-audit.md}.</p>
 *
 * <p>Behaviour:</p>
 * <ul>
 *   <li>{@code allow-local-fallback=false} (default, production-safe) — when the
 *       upstream wallet service fails or returns no wallet, the BFF returns
 *       HTTP 503 with stable error code {@code WALLET_UPSTREAM_UNAVAILABLE}.
 *       The BFF must not silently fabricate wallet balances, wallet
 *       transactions, or payment states.</li>
 *   <li>{@code allow-local-fallback=true} (dev/test only) — when the upstream
 *       wallet service fails or returns no wallet, the BFF falls back to the
 *       existing in-memory shape so local development continues to function.
 *       Every fallback activation is logged at {@code WARN} level with a
 *       stable {@code WALLET FALLBACK ACTIVATED} marker so the activation is
 *       auditable after the fact.</li>
 * </ul>
 *
 * <p>Environment override: {@code IMPILO_WALLET_ALLOW_LOCAL_FALLBACK}.</p>
 */
@ConfigurationProperties(prefix = "impilo.wallet")
public class BffWalletProperties {

    /**
     * When {@code true} the BFF may serve in-memory fallback wallet/payment data
     * if the upstream Mushe Wallet service is unavailable. Default {@code false}
     * — production must not silently fabricate wallet state.
     */
    private boolean allowLocalFallback = false;

    public boolean isAllowLocalFallback() {
        return allowLocalFallback;
    }

    public void setAllowLocalFallback(boolean allowLocalFallback) {
        this.allowLocalFallback = allowLocalFallback;
    }
}
