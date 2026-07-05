package zw.gov.mohcc.impilo.varapi.enums;

/**
 * The <b>honest registry answer</b> for a provider — what the national registry can
 * truthfully say about this person's standing right now (IATG Wave 1).
 *
 * <p>This is an <b>additive fifth status axis</b> next to the four legacy axes on the
 * provider row ({@code status}, {@code licenceStatus}, {@code professionalStandingStatus},
 * {@code activeFlag}), which remain untouched here and are consolidated in Wave&nbsp;2.
 * Unlike those operational flags, this axis encodes what the registry has actually
 * verified — including partial-evidence states such as
 * {@link #MATCHED_EMPLOYMENT_ONLY} and honest uncertainty ({@link #PENDING_VERIFICATION},
 * {@link #CONFLICT}) — so downstream consumers never have to infer verification depth
 * from operational status.</p>
 */
public enum ProviderRegistryStatus {

    /** Registered with the relevant council and the registration is current. */
    REGISTERED_ACTIVE,

    /** Registered, but the council registration has expired. */
    REGISTERED_EXPIRED,

    /** Suspended by the regulator. */
    SUSPENDED,

    /** Removed from the register by the regulator. */
    DEREGISTERED,

    /** The registry holds authoritative evidence the person is deceased. */
    DECEASED,

    /** No verified evidence yet — the registry cannot vouch either way. */
    PENDING_VERIFICATION,

    /** Evidence sources disagree (e.g. council record vs identity/employment mismatch). */
    CONFLICT,

    /** Matched against workforce/employment evidence only — no council match yet. */
    MATCHED_EMPLOYMENT_ONLY,

    /** Matched against BOTH council registry and workforce/employment evidence. */
    MATCHED_BOTH
}
