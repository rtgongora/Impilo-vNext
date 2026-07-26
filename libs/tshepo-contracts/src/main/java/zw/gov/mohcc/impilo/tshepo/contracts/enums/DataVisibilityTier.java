package zw.gov.mohcc.impilo.tshepo.contracts.enums;

/**
 * Maximum data representation the actor may receive for the current request.
 * Authority (role, assignment) does not imply this tier — policy binds them.
 */
public enum DataVisibilityTier {
    AGGREGATE_ONLY,
    DEIDENTIFIED_ROW_LEVEL,
    PSEUDONYMISED_PERSON_LEVEL,
    IDENTIFIED_OPERATIONAL_ONLY,
    IDENTIFIED_LIMITED_CLINICAL,
    FULL_IDENTIFIED_CLINICAL,

    /**
     * Strictly above {@link #FULL_IDENTIFIED_CLINICAL}: the actor may additionally receive content
     * classified {@code SPECIALLY_PROTECTED} (sexual and reproductive health, HIV, mental health,
     * safeguarding). No purpose-of-use grants this tier by default — it must be granted explicitly
     * by a governed policy rule, by the subject reading their own record, or by an audited
     * break-glass. That default-withhold is what makes the class enforcing rather than decorative.
     *
     * <p>Appended last on purpose: existing constants keep their ordinals.</p>
     */
    SPECIALLY_PROTECTED_CLINICAL;

    public static DataVisibilityTier fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return IDENTIFIED_OPERATIONAL_ONLY;
        }
        try {
            return DataVisibilityTier.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return IDENTIFIED_OPERATIONAL_ONLY;
        }
    }

    /** Whether row-level person-linked payloads are allowed at all. */
    public boolean allowsRowLevel() {
        return this != AGGREGATE_ONLY;
    }

    /** Whether direct identifiers (name, phone, national id, etc.) may appear. */
    public boolean allowsDirectIdentifiers() {
        return this == IDENTIFIED_OPERATIONAL_ONLY
                || this == IDENTIFIED_LIMITED_CLINICAL
                || this == FULL_IDENTIFIED_CLINICAL
                || this == SPECIALLY_PROTECTED_CLINICAL;
    }

    /** Whether unrestricted clinical narrative/detail is allowed. */
    public boolean allowsFullClinical() {
        return this == FULL_IDENTIFIED_CLINICAL || this == SPECIALLY_PROTECTED_CLINICAL;
    }

    /**
     * Whether content classified {@code SPECIALLY_PROTECTED} may be disclosed to this actor.
     * This is the question a PEP asks before returning — or including in a collection — a record
     * carrying that class. Only the top tier answers yes.
     */
    public boolean allowsSpeciallyProtected() {
        return this == SPECIALLY_PROTECTED_CLINICAL;
    }

    /** Higher means more disclosure (for max/min composition in PDP/PEP). */
    public int disclosureLevel() {
        return switch (this) {
            case AGGREGATE_ONLY -> 0;
            case DEIDENTIFIED_ROW_LEVEL -> 1;
            case PSEUDONYMISED_PERSON_LEVEL -> 2;
            case IDENTIFIED_OPERATIONAL_ONLY -> 3;
            case IDENTIFIED_LIMITED_CLINICAL -> 4;
            case FULL_IDENTIFIED_CLINICAL -> 5;
            case SPECIALLY_PROTECTED_CLINICAL -> 6;
        };
    }

    public static DataVisibilityTier max(DataVisibilityTier a, DataVisibilityTier b) {
        return a.disclosureLevel() >= b.disclosureLevel() ? a : b;
    }

    public static DataVisibilityTier min(DataVisibilityTier a, DataVisibilityTier b) {
        return a.disclosureLevel() <= b.disclosureLevel() ? a : b;
    }
}
