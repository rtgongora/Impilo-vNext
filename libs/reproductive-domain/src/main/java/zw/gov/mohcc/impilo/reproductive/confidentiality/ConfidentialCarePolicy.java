package zw.gov.mohcc.impilo.reproductive.confidentiality;

import java.util.Optional;

/**
 * The governed national policy a confidentiality stamp depends on.
 *
 * <p><strong>There is deliberately no {@code engineeringSeed()} factory here</strong>, unlike its
 * sibling {@code LossThresholdPolicy}. That asymmetry is the point. A seeded gestational threshold
 * mis-classifies a loss, which is a data-quality problem. A seeded CONSENT AGE decides whether a
 * teenager's record is hidden from her parents, and getting it wrong in either direction causes
 * real harm — hiding a record from a guardian who is legally entitled to it, or exposing a young
 * person who was promised confidentiality. A compiled-in default would make the wrong answer the
 * silent one.
 *
 * <p>So: no policy parameter available means no age, which means no age-based stamp. The caller
 * records that as {@code POLICY_UNAVAILABLE} rather than substituting a number.
 *
 * <p>Zimbabwean consent ages are not uniform across services — independent consent for HIV testing,
 * contraception and mental-health care sit under different instruments amended at different times —
 * which is why the value is fetched per category and per date rather than held as one constant.
 */
public interface ConfidentialCarePolicy {

    /**
     * The age below which a record in this category is confidential from a guardian, if a governed
     * parameter is in force. {@link Optional#empty()} means no parameter was available — never a
     * fallback value.
     */
    Optional<Integer> confidentialFromGuardianAgeYears(ConfidentialityCategory category);

    /**
     * Whether a non-termination pregnancy loss is confidential from a guardian for a young person
     * below the age above. Empty when no governed parameter is in force.
     */
    Optional<Boolean> nonTerminationLossConfidentialFromGuardian();

    /** The policy version that answered, so a past stamp stays re-explainable. */
    String contentVersion();

    /**
     * Whether the answering parameter is ratified national policy rather than an engineering seed.
     * A consumer may READ a seed — it needs to know a value exists and is unverified — but must not
     * apply a protection class from one.
     */
    boolean ratified();
}
