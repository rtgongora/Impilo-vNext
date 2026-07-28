package zw.gov.mohcc.impilo.reproductive.confidentiality;

/**
 * The governed confidential-category vocabulary.
 *
 * <p>Mirrors the six codes seeded by zibo {@code V008} and listed in tshepo-authz's
 * {@code adolescent-confidentiality-pack.json} {@code protectedCategories}. It is duplicated here
 * deliberately rather than imported: this library is Spring-free and service-free by design, and a
 * drift between the two lists is exactly what the CHECK constraint in pct V437 and the pack's own
 * vocabulary will catch.
 *
 * <p>Grants are category-scoped, so a safeguarding lead can hold safeguarding without thereby
 * holding an adolescent's mental-health notes. That is why this is an enumeration and not a boolean.
 */
public enum ConfidentialityCategory {

    SEXUAL_REPRODUCTIVE_HEALTH,
    HIV,
    MENTAL_HEALTH,
    SAFEGUARDING,
    SUBSTANCE_USE,
    GENDER_BASED_VIOLENCE;

    public String code() {
        return name();
    }
}
