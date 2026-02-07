package zw.gov.mohcc.impilo.oros.domain;

/**
 * Types of worksteps within an order's fulfillment workflow.
 *
 * <p>Each order type has a predefined workstep template. For example,
 * a LAB order typically flows through: ORDER_PLACED -> SPECIMEN_COLLECTION
 * -> SPECIMEN_RECEIVED -> ANALYSIS -> REPORTING -> CLOSE_OUT.</p>
 *
 * <p>An IMAGING order would use: ORDER_PLACED -> IMAGING_ACQUISITION
 * -> IMAGING_REPORTING -> CLOSE_OUT.</p>
 *
 * <p>A PHARMACY order would use: ORDER_PLACED -> DISPENSE -> ADMINISTER
 * (if inpatient) -> CLOSE_OUT.</p>
 */
public enum WorkstepType {
    /** Order has been placed and is awaiting processing. */
    ORDER_PLACED,
    /** Specimen collection from the patient. */
    SPECIMEN_COLLECTION,
    /** Specimen received at the processing lab. */
    SPECIMEN_RECEIVED,
    /** Laboratory analysis in progress. */
    ANALYSIS,
    /** Report generation (lab or general). */
    REPORTING,
    /** Imaging acquisition (X-ray, ultrasound, etc.). */
    IMAGING_ACQUISITION,
    /** Imaging interpretation and reporting. */
    IMAGING_REPORTING,
    /** Medication dispensing. */
    DISPENSE,
    /** Medication administration (inpatient). */
    ADMINISTER,
    /** Final close-out and archival. */
    CLOSE_OUT
}
