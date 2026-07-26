package zw.gov.mohcc.impilo.reproductive.contraception;

/**
 * Whether a method is still protecting.
 *
 * <p>{@link #UNKNOWN} exists so that a method nobody can date is never reported as covering. A
 * woman shown as protected by an implant with no insertion date is the failure this whole type
 * exists to prevent: it is a confident answer produced from an absence, and she acts on it.
 */
public enum CoverageStatus {
    /** In date. */
    COVERED,
    /** Due now — protection continues, but the next dose or replacement is owed. */
    DUE,
    /** Past due but inside the grace window where protection is retained. */
    LATE_WITHIN_GRACE,
    /** Past the grace window. Protection can no longer be assumed. */
    LAPSED,
    /** A device or implant past its clinical life. */
    EXPIRED,
    /** The method has no expiry to compute — condoms, sterilisation, withdrawal. */
    NOT_APPLICABLE,
    /** Not enough recorded to say. Never treat as covered. */
    UNKNOWN
}
