package zw.gov.mohcc.impilo.tshepo.authz.shadow;

import java.util.List;
import java.util.Locale;

/**
 * Derives the proposed principal model from a validated session, for measurement only.
 *
 * <p><b>Why this exists rather than a fix to {@code KeycloakAdapter.deriveActorType()}.</b> That
 * method reads an {@code actor_type} claim the realm does not mint (verified against Keycloak's
 * own database: the only trust-header mapper is {@code x_actor_id}), then falls back to comparing
 * <em>lowercase</em> literals against realm roles that are <em>UPPERCASE</em>, so no branch can
 * match, and returns {@code "PROVIDER"}. Repairing it in place would produce a fourth
 * authorization vocabulary with a bug removed. This projects the intended model instead, and
 * nothing here is enforced.</p>
 *
 * <h2>The model</h2>
 * <ul>
 *   <li><b>principalClass</b> — {@code HUMAN | SERVICE | SYSTEM}. Mutually exclusive: what kind of
 *       principal this is, never what it may do.</li>
 *   <li><b>facets</b> — non-exclusive and simultaneously true. A provider <em>is</em> also a
 *       citizen and may also act as a proxy. Facets are not modelled here yet because this stage
 *       measures roles and personas; they arrive with the Principal Token.</li>
 *   <li><b>activePersona</b> — exactly one, and only ever a <em>proven</em> one.</li>
 * </ul>
 *
 * <h2>The rule that matters</h2>
 * <p><b>Possession of a Keycloak role never selects a persona.</b> A person holding
 * {@code REGISTRY_ADMIN} with no proven registry appointment is {@code MY_LIFE}, not an
 * administrator. Roles resolve capabilities <em>inside</em> an already-proven context; they do not
 * establish the context. Until the work-context machinery feeds this projection, an absent proven
 * context therefore yields {@code MY_LIFE} — the narrowest answer, not the most flattering one.</p>
 */
public final class PrincipalProjection {

    public enum PrincipalClass { HUMAN, SERVICE, SYSTEM }

    /** Personas with no work anchor. Work personas are the server {@code WorkMode} values verbatim. */
    public static final String PERSONA_MY_LIFE = "MY_LIFE";
    public static final String PERSONA_MY_PROFESSIONAL = "MY_PROFESSIONAL";
    /** Not reachable yet: no proxy/caregiver persona is represented end to end in the estate. */
    public static final String PERSONA_PROXY = "PROXY";

    private PrincipalProjection() {}

    /**
     * @param roles        realm roles from the validated token (may be empty)
     * @param workMode     the mode from a PROVEN duty context, or null when none is active
     * @param providerId   a resolved professional identity, or null
     * @param subjectIsService true when the token is a workload/service-account credential
     */
    public static Projection project(List<String> roles, String workMode,
                                     String providerId, boolean subjectIsService) {
        PrincipalClass principalClass = subjectIsService ? PrincipalClass.SERVICE : PrincipalClass.HUMAN;

        String persona;
        if (principalClass == PrincipalClass.SERVICE) {
            persona = "SERVICE";
        } else if (workMode != null && !workMode.isBlank()) {
            // A proven duty context. The WorkMode IS the persona — it already declares its anchor
            // kind, clinical-data access and purpose of use, so no parallel vocabulary is invented.
            persona = workMode.trim().toUpperCase(Locale.ROOT);
        } else if (providerId != null && !providerId.isBlank()) {
            // A professional identity with no work anchor: My Professional, not Work.
            persona = PERSONA_MY_PROFESSIONAL;
        } else {
            // No proven context. Deliberately NOT derived from roles — see the class doc.
            persona = PERSONA_MY_LIFE;
        }

        return new Projection(principalClass, persona, legacyActorType(principalClass, persona, providerId));
    }

    /**
     * Transitional projection onto the legacy {@code actorType} vocabulary, so the 352 existing
     * {@code policy_rule.actor_type} rules keep matching exactly as they do today. Deleting this is
     * P4 work, not now — and it is derived from the proven context, never from a Keycloak role.
     */
    static String legacyActorType(PrincipalClass principalClass, String persona, String providerId) {
        if (principalClass == PrincipalClass.SERVICE) return "SERVICE";
        if (principalClass == PrincipalClass.SYSTEM) return "SYSTEM";
        if (PERSONA_MY_LIFE.equals(persona)) return "CITIZEN";
        if (PERSONA_PROXY.equals(persona)) return "CAREGIVER";
        if (PERSONA_MY_PROFESSIONAL.equals(persona)) {
            // My Professional without a professional identity is not a provider.
            return (providerId != null && !providerId.isBlank()) ? "PROVIDER" : "CITIZEN";
        }
        // A proven WorkMode. Modes that grant identified clinical access are provider capacity;
        // the rest are operational. Kept as an explicit list rather than a substring test so a new
        // mode has to be classified deliberately.
        return switch (persona) {
            case "CLINICAL_CARE", "VIRTUAL_CARE", "COMMUNITY_OUTREACH" -> "PROVIDER";
            default -> "OPERATOR";
        };
    }

    /** @param legacyActorType the transitional projection — never a new vocabulary. */
    public record Projection(PrincipalClass principalClass, String activePersona, String legacyActorType) {}
}
