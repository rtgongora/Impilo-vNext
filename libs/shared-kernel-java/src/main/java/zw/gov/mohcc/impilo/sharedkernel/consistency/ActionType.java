package zw.gov.mohcc.impilo.sharedkernel.consistency;

/**
 * Actions that the consistency gate evaluates to determine
 * whether synchronous spine authority is required before proceeding.
 */
public enum ActionType {

    MSIKA_UPDATE_TARIFF,
    VITO_PATIENT_MERGE,
    CLINICAL_PRESCRIBE_CONTROLLED_SUBSTANCE,
    UNKNOWN
}
