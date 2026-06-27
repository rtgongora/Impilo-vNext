package zw.gov.mohcc.impilo.tshepo.offline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for the TSHEPO Offline Trust Controls service.
 *
 * <p>Controls capability token TTL, maximum offline encounters,
 * allowed offline actions, offline pack TTL, and reconciliation batch sizes.</p>
 */
@ConfigurationProperties(prefix = "tshepo.offline")
public record OfflineProperties(
        /** Default TTL in hours for offline capability tokens. */
        int defaultCapabilityTtlHours,
        /** Maximum allowed TTL in hours for offline capability tokens. */
        int maxCapabilityTtlHours,
        /** Maximum number of provisional encounters allowed per offline session. */
        int maxOfflineEncounters,
        /** Whitelist of actions that may be performed offline. */
        List<String> allowedOfflineActions,
        /** Default TTL in hours for offline packs. */
        int offlinePackTtlHours,
        /** Maximum number of actions to reconcile in a single batch. */
        int reconciliationBatchSize,
        /** Expected {@code iss} claim on capability tokens (minted and verified). */
        String tokenIssuer,
        /** Expected {@code aud} claim on capability tokens (minted and verified). */
        String tokenAudience,
        /**
         * TTL in seconds for the locally-cached JWKS. Within the TTL, token verification
         * uses the cached keys and never contacts the keys-service — verification works
         * truly offline. On a fetch failure the last-known-good JWKS is retained.
         */
        int jwksCacheTtlSeconds
) {
    public OfflineProperties {
        if (defaultCapabilityTtlHours <= 0) {
            defaultCapabilityTtlHours = 8;
        }
        if (maxCapabilityTtlHours <= 0) {
            maxCapabilityTtlHours = 72;
        }
        if (maxOfflineEncounters <= 0) {
            maxOfflineEncounters = 50;
        }
        if (allowedOfflineActions == null || allowedOfflineActions.isEmpty()) {
            allowedOfflineActions = List.of(
                "READ_PATIENT",
                "CREATE_PROVISIONAL_ENCOUNTER",
                "CREATE_PROVISIONAL_REGISTRATION",
                "READ_MEDICATION",
                "DISPENSE_MEDICATION"
            );
        }
        if (offlinePackTtlHours <= 0) {
            offlinePackTtlHours = 24;
        }
        if (reconciliationBatchSize <= 0) {
            reconciliationBatchSize = 100;
        }
        if (tokenIssuer == null || tokenIssuer.isBlank()) {
            tokenIssuer = "tshepo-offline-service";
        }
        if (tokenAudience == null || tokenAudience.isBlank()) {
            tokenAudience = "impilo-offline";
        }
        if (jwksCacheTtlSeconds <= 0) {
            jwksCacheTtlSeconds = 3600;
        }
    }
}
