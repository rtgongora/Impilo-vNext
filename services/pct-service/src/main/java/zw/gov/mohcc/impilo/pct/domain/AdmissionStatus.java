package zw.gov.mohcc.impilo.pct.domain;

/**
 * Status of an inpatient admission.
 *
 * <ul>
 *   <li>{@link #REQUESTED} — admission has been requested by a clinician</li>
 *   <li>{@link #APPROVED} — admission approved, bed assignment pending</li>
 *   <li>{@link #ADMITTED} — patient is admitted and occupying a bed</li>
 *   <li>{@link #TRANSFERRED} — patient transferred to another ward/facility</li>
 *   <li>{@link #DISCHARGED} — patient discharged from inpatient care</li>
 *   <li>{@link #CANCELLED} — admission request was cancelled</li>
 * </ul>
 */
public enum AdmissionStatus {
    REQUESTED,
    APPROVED,
    ADMITTED,
    TRANSFERRED,
    DISCHARGED,
    CANCELLED
}
