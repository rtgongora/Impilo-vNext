package zw.gov.mohcc.impilo.varapi.enums;

import java.util.Locale;

/**
 * Platform-wide provider trust ladder (IATG Wave 1).
 *
 * <p>Ordinal order is meaningful: trust upgrades are monotonic
 * ({@code SELF_ASSERTED} &rarr; {@code FULLY_LINKED_OR_ONBOARDED}); downgrades are only
 * permitted through an explicit evidence-revoked path with a recorded reason
 * (see {@code ProviderTrustService}).</p>
 *
 * <p><b>Mapping of the external-collaboration 5-level ladder</b>
 * (string constants historically defined on
 * {@code ExternalProviderCollaborationService} and persisted on
 * {@code external_provider_access_profile.trust_level} — those stored strings are
 * untouched; this enum is the shared semantic anchor):</p>
 *
 * <ul>
 *   <li>{@code SELF_ASSERTED} &rarr; {@link #SELF_ASSERTED}</li>
 *   <li>{@code COUNCIL_NUMBER_FORMAT_VALID} &rarr; {@link #DOCUMENT_SUPPORTED}
 *       (a well-formed council number is supporting documentation, not a registry match)</li>
 *   <li>{@code COUNCIL_NUMBER_FOUND} &rarr; {@link #DOCUMENT_SUPPORTED}
 *       (registry hit without identity alignment is still only document-grade support)</li>
 *   <li>{@code COUNCIL_AND_IDENTITY_MATCH} &rarr; {@link #COUNCIL_VERIFIED}</li>
 *   <li>{@code FULLY_LINKED_OR_ONBOARDED} &rarr; {@link #FULLY_LINKED_OR_ONBOARDED}</li>
 * </ul>
 */
public enum ProviderTrustLevel {

    /** Only the person's own assertion backs this profile. */
    SELF_ASSERTED,

    /**
     * Supporting documents / well-formed or registry-located council numbers exist,
     * but no identity-aligned authoritative match has been made.
     */
    DOCUMENT_SUPPORTED,

    /** Matched against health-workforce employment records (e.g. HSC employment). */
    EMPLOYMENT_MATCHED,

    /** Council registry record found AND identity-aligned to this person. */
    COUNCIL_VERIFIED,

    /** Fully linked to an onboarded platform provider profile (terminal trust). */
    FULLY_LINKED_OR_ONBOARDED;

    // ── External-collaboration ladder aliases ────────────────────────────────
    // String values are IDENTICAL to the legacy literals so no stored data or
    // wire payload changes; ExternalProviderCollaborationService points its
    // public constants here.

    public static final String EXTERNAL_SELF_ASSERTED = "SELF_ASSERTED";
    public static final String EXTERNAL_COUNCIL_NUMBER_FORMAT_VALID = "COUNCIL_NUMBER_FORMAT_VALID";
    public static final String EXTERNAL_COUNCIL_NUMBER_FOUND = "COUNCIL_NUMBER_FOUND";
    public static final String EXTERNAL_COUNCIL_AND_IDENTITY_MATCH = "COUNCIL_AND_IDENTITY_MATCH";
    public static final String EXTERNAL_FULLY_LINKED_OR_ONBOARDED = "FULLY_LINKED_OR_ONBOARDED";

    /**
     * Map an external-collaboration ladder label onto the shared trust level.
     *
     * @throws IllegalArgumentException for unknown labels
     */
    public static ProviderTrustLevel fromExternalLabel(String label) {
        if (label == null) {
            throw new IllegalArgumentException("external trust label is required");
        }
        return switch (label.trim().toUpperCase(Locale.ROOT)) {
            case EXTERNAL_SELF_ASSERTED -> SELF_ASSERTED;
            case EXTERNAL_COUNCIL_NUMBER_FORMAT_VALID, EXTERNAL_COUNCIL_NUMBER_FOUND -> DOCUMENT_SUPPORTED;
            case EXTERNAL_COUNCIL_AND_IDENTITY_MATCH -> COUNCIL_VERIFIED;
            case EXTERNAL_FULLY_LINKED_OR_ONBOARDED -> FULLY_LINKED_OR_ONBOARDED;
            default -> throw new IllegalArgumentException("Unknown external trust label: " + label);
        };
    }

    /** True when moving from {@code this} to {@code target} does not lose trust. */
    public boolean canUpgradeTo(ProviderTrustLevel target) {
        return target != null && target.ordinal() >= this.ordinal();
    }
}
