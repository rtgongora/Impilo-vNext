package zw.gov.mohcc.impilo.pharmacy.domain;

/**
 * Types of drug substitution rules ({@code rx_substitution_rules.rule_type}).
 *
 * <p>Governs which alternative drugs may be dispensed when the
 * originally prescribed drug is unavailable or when a formulary
 * policy mandates substitution.</p>
 */
public enum SubstitutionRuleType {

    /** Substitute with the generic equivalent of a branded drug. */
    GENERIC,

    /** Substitute within the same therapeutic class (ATC grouping). */
    THERAPEUTIC_CLASS,

    /** Substitute with a different strength or dosage form of the same drug. */
    STRENGTH_FORM,

    /** No substitution permitted; the exact drug must be dispensed. */
    NO_SUBSTITUTION
}
