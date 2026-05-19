package zw.gov.mohcc.impilo.mushex.service.rail;

import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterReadinessStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;

/**
 * Read-only descriptor for a single payment-rail adapter's readiness, as exposed
 * by {@link AdapterReadinessService}.
 *
 * <p>Carries the {@link AdapterType}, the deterministically-derived
 * {@link AdapterReadinessStatus}, two capability booleans, and a short
 * human-readable detail string suitable for an operator UI.
 *
 * <p>Never contains credentials or any field that could move money. Safe to
 * serialise across the BFF boundary.
 */
public record AdapterReadiness(
        AdapterType adapterType,
        AdapterReadinessStatus status,
        boolean liveCapable,
        boolean sandboxCapable,
        String detail
) {
    public AdapterReadiness {
        if (adapterType == null) {
            throw new IllegalArgumentException("adapterType must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }
}
