package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

/**
 * C3 silent identifier-resolution response.
 *
 * <p><b>Anti-enumeration:</b> on failure this carries {@code resolved=false} and a
 * {@code null} {@link PersonRef} with no reason — the caller cannot distinguish
 * "no such identifier" from "identifier exists but is not resolvable". The same
 * shape and a constant-time floor are applied to both outcomes.</p>
 *
 * <p>{@code canAuthenticate} is always {@code false} for {@code PROVIDER_ID} and
 * {@code COUNCIL_NUMBER}: those resolve a profile but never authenticate.</p>
 */
public record IdentifierResolveResponse(
        boolean resolved,
        PersonRef personRef,
        boolean canAuthenticate
) {
    public static IdentifierResolveResponse notResolved() {
        return new IdentifierResolveResponse(false, null, false);
    }

    public record PersonRef(
            String healthId,
            String cpid,
            boolean provisional,
            String assuranceLevel   // VERIFIED | UNVERIFIED
    ) {
    }
}
