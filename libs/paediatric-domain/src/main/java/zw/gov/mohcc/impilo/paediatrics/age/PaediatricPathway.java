package zw.gov.mohcc.impilo.paediatrics.age;

/**
 * Clinical pathways a child can be routed into. The router recommends one; a clinician
 * may always choose another, which is why this is a recommendation type and not a lock.
 */
public enum PaediatricPathway {

    /** Delivery through initial newborn stabilisation. */
    BIRTH_EPISODE,

    /** Routine newborn assessment in the first days of life. */
    NEWBORN_ROUTINE,

    /** Unwell infant aged 0–59 days: the dedicated young-infant assessment. */
    SICK_YOUNG_INFANT,

    /** Acute illness in a child 2 months–5 years: IMNCI-compatible assessment. */
    IMNCI_UNDER_FIVE,

    /** Routine growth, immunisation and development visit. */
    WELL_CHILD,

    /** Full age-appropriate clerking for a child 5 years and above. */
    GENERAL_PAEDIATRIC,

    /** Confidential adolescent encounter. */
    ADOLESCENT,

    /** Planned handover from paediatric to adult services. */
    TRANSITION_TO_ADULT,

    /** Age is unknown; no pathway can be safely recommended. */
    UNDETERMINED
}
