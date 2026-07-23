package zw.gov.mohcc.impilo.oros.domain;

/**
 * Prescription aggregate lifecycle (Vol II §9.2).
 *
 * <ul>
 *   <li>{@code DRAFT} — authored, not yet signed; freely editable</li>
 *   <li>{@code SIGNED_ACTIVE} — current version signed + activated; the ONLY claimable state</li>
 *   <li>{@code SUPERSEDED} — reserved for cross-prescription replacement (a new prescription
 *       supersedes this one); within one aggregate, amendment supersedes VERSIONS, not the parent</li>
 *   <li>{@code EXHAUSTED} — final claim decremented repeats to 0; terminal</li>
 *   <li>{@code EXPIRED} — validity window lapsed; terminal</li>
 *   <li>{@code CANCELLED} — prescriber cancel (unclaimed remainder only); terminal</li>
 *   <li>{@code ENTERED_IN_ERROR} — governance error-marking, no dispense occurred; terminal</li>
 * </ul>
 */
public enum PrescriptionStatus {
    DRAFT,
    SIGNED_ACTIVE,
    SUPERSEDED,
    EXHAUSTED,
    EXPIRED,
    CANCELLED,
    ENTERED_IN_ERROR
}
