package zw.gov.mohcc.impilo.tshepo.contracts.enums;

/**
 * HL7 / NIST purpose-of-use codes.
 * Every request must declare its purpose; TSHEPO evaluates whether the
 * actor + context + purpose combination is allowed.
 */
public enum PurposeOfUse {
    /** Direct patient care. */
    TREATMENT,
    /**
     * Coordination of care across providers, facilities or services — referrals, care-team
     * messaging, patient-share collaboration, delivery write-backs. HL7 ActReason models this
     * (COC) as a specialization of TREAT, so it carries the same visibility envelope as
     * {@link #TREATMENT}; it exists as a distinct code so the decision log can tell
     * coordination traffic apart from direct care.
     */
    CARE_COORDINATION,
    /** Billing, claims, reimbursement. */
    PAYMENT,
    /** Facility operations, scheduling, queue management. */
    OPERATIONS,
    /** De-identified research data access. */
    RESEARCH,
    /** Surveillance, outbreak management. */
    PUBLIC_HEALTH,
    /**
     * A person acting on their own record in their own person context — citizen messaging,
     * self-booking, viewing own results. Distinct from {@link #TREATMENT}: a citizen reading
     * their own chart is not a clinician delivering care, and must not inherit a clinician's
     * envelope over anyone else's data.
     */
    SELF_SERVICE,
    /**
     * Regulatory oversight duty — a council or authority acting on its statutory mandate
     * (registration, inspection, complaints, committee dockets). Deliberately carries no
     * clinical access: a regulator inspecting a practitioner must not thereby see patient
     * clinical detail. Minted as the purpose of a WORK_CONTEXT regulatory duty token.
     */
    REGULATORY_DUTY,
    /** Emergency access — elevated audit, break-glass pre-requisite. */
    EMERGENCY,
    /** Override access — requires step-up + reason + mandatory review. */
    BREAK_GLASS,
    /** Machine-to-machine operations (schedulers, outbox publishers, etc.). */
    SYSTEM
}
