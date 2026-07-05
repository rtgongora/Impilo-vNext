package zw.gov.mohcc.impilo.varapi.enums;

import java.util.Locale;

/**
 * How a provider entered the platform (IATG Wave 1 channel typing).
 *
 * <p>Channels are provenance, not trust: a channel never raises
 * {@link ProviderTrustLevel} by itself — trust only moves through evidence-backed
 * transitions in {@code ProviderTrustService}.</p>
 */
public enum OnboardingChannel {

    /** Channel A — council / regulator import (authoritative regulatory source). */
    REGULATORY_A,

    /** Channel B — workforce bulk preload (e.g. HSC / employer workforce lists). */
    WORKFORCE_B,

    /** Channel C — organisation-initiated onboarding / attestation. */
    ORGANIZATION_C,

    /** Self-registration by the person themselves. */
    SELF;

    /**
     * Map a {@code ProviderEntity.bootstrapOrigin} value onto its channel:
     * {@code BULK_PRELOAD} &rarr; {@link #WORKFORCE_B},
     * {@code COUNCIL_IMPORT} &rarr; {@link #REGULATORY_A},
     * {@code SELF_REGISTERED} &rarr; {@link #SELF}.
     * (This mapping is mirrored by the V017 backfill migration.)
     *
     * @return the mapped channel, or {@code null} for unknown/absent origins
     */
    public static OnboardingChannel fromBootstrapOrigin(String bootstrapOrigin) {
        if (bootstrapOrigin == null) {
            return null;
        }
        return switch (bootstrapOrigin.trim().toUpperCase(Locale.ROOT)) {
            case "BULK_PRELOAD" -> WORKFORCE_B;
            case "COUNCIL_IMPORT" -> REGULATORY_A;
            case "SELF_REGISTERED" -> SELF;
            default -> null;
        };
    }
}
