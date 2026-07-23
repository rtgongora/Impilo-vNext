package zw.gov.mohcc.impilo.telemonitoring.domain;

/**
 * Calibration posture stamped onto an assignment-gate lookup (OF-B24 → consumed by the
 * OF-B25 ingestion gate). Projected fresh from asset-registry at resolve time — never
 * cached as clinical truth here (asset-registry stays the physical/calibration SoR).
 *
 * <p>Doctrine (§15.5 invariant): a lapsed-calibration or unverifiable device is
 * <b>stamped, never blocked</b> at the lookup — readings still flow, carrying this
 * posture so the rules engine can exclude them from value-alerting while surfacing them
 * as device-quality signals. Nothing is silently dropped (§15.7).</p>
 */
public enum CalibrationPosture {
    /** Asset known, calibration current (or asset carries no calibration schedule). */
    OK,
    /** Asset known but calibration lapsed / equipment flagged — exclude from value-alerting. */
    DEGRADED,
    /** Asset-registry unreachable or the referenced asset is unknown — could not verify. */
    UNVERIFIED,
    /** No asset_ref on the assignment: no physical-truth leg to project (e.g. BYOD pending OD-14). */
    UNTRACKED
}
