package zw.gov.mohcc.impilo.vito.core.proofing;

import zw.gov.mohcc.impilo.vito.core.ProofingMethod;

/**
 * The assurance-<b>outcome</b> ladder (Identity Journey Doctrine §4). Every proofing run
 * resolves to one of these outcomes, each carrying the numeric assurance level (LOA) it
 * grants and the ledger {@link ProofingMethod} it records under. This is the code-side
 * anchor for the doctrine table so a journey cannot invent an ungoverned assurance jump.
 */
public enum ProofingOutcome {

    /** Phone/email + demographics only. */
    SELF_ASSERTED(1, ProofingMethod.PORTAL),
    /** Existing Impilo credential + OTP/corroboration to a recorded contact. */
    RECORD_LINKED(2, ProofingMethod.PORTAL),
    /** Valid ID document reviewed (OCR + integrity). */
    DOCUMENT_VERIFIED(2, ProofingMethod.DOCUMENT),
    /** Authorised video-verification decision by an officer. */
    REMOTELY_WITNESSED(2, ProofingMethod.ASSISTED),
    /** CRVS / National-ID authoritative source match (via UBOMI, X2). */
    AUTHORITATIVELY_VERIFIED(3, ProofingMethod.MOSIP_INDIRECT),
    /** Verified biometric bound to the HID (ABIS, X1). */
    BIOMETRICALLY_BOUND(3, ProofingMethod.BIOMETRIC),
    /** Authorised officer physically inspected the evidence. */
    IN_PERSON_WITNESSED(3, ProofingMethod.ASSISTED),
    /** Identity is contested — grants no assurance and blocks reliant actions. */
    DISPUTED(0, ProofingMethod.ASSISTED);

    private final int assuranceLevel;
    private final ProofingMethod ledgerMethod;

    ProofingOutcome(int assuranceLevel, ProofingMethod ledgerMethod) {
        this.assuranceLevel = assuranceLevel;
        this.ledgerMethod = ledgerMethod;
    }

    public int assuranceLevel() {
        return assuranceLevel;
    }

    public ProofingMethod ledgerMethod() {
        return ledgerMethod;
    }
}
