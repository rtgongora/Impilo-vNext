package zw.gov.mohcc.impilo.oros.domain;

/**
 * Lifecycle state of a laboratory specimen (spec §7). Distinct from the order's
 * {@link LabWorkflowState} — a specimen's own physical journey from the patient to the analysing
 * lab, including the rejection/loss/recollection exceptions.
 */
public enum SpecimenStatus {
    /** Drawn/obtained from the patient. */
    COLLECTED,
    /** Labelled/barcoded with the sample identifier. */
    LABELLED,
    /** Sent from the collection point toward the lab. */
    DISPATCHED,
    /** In transit between facilities. */
    IN_TRANSIT,
    /** Received and accessioned at the analysing lab. */
    RECEIVED,
    /** Rejected at receipt (e.g. haemolysed, mislabelled, insufficient volume). */
    REJECTED,
    /** Lost in transit or before analysis. */
    LOST,
    /** Superseded by a recollection request. */
    RECOLLECTED
}
