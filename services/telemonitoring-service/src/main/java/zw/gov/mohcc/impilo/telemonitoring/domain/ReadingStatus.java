package zw.gov.mohcc.impilo.telemonitoring.domain;

/**
 * Clinical-lane posture of a consumed telemetry reading (OF-B25, §14.5 step 11 / §15.7).
 *
 * <p>Doctrine: nothing is silently dropped. A reading that cannot be attributed to its
 * patient is QUARANTINED — retained with a coded reason and NULL patient attribution
 * (journey #62: a wrong-record write is the failure criterion). A reading from a
 * degraded/unverifiable device is DEGRADED_VALID — stamped, never dropped, and gated
 * out of value-alerting by OF-B26, not by this service.</p>
 */
public enum ReadingStatus {
    /** Attributed, calibration posture OK — eligible for the single-writer SHR path. */
    VALID,
    /** Attributed but the device's calibration posture is not OK — kept WITH its stamp. */
    DEGRADED_VALID,
    /** Not attributable (or unusable payload) — retained as evidence, never written to any record. */
    QUARANTINED
}
