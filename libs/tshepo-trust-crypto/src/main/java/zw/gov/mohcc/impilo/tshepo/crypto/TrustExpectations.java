package zw.gov.mohcc.impilo.tshepo.crypto;

import java.util.Set;

/**
 * Optional claim expectations applied after the signature has been verified. Any field
 * left null/empty is not checked, so the same primitive serves callers that only need
 * cryptographic verification and callers that also bind issuer/audience/purpose.
 *
 * @param allowedIssuers  if non-empty, the token {@code iss} must be one of these
 * @param requiredAudience if non-null, the token {@code aud} must contain this value
 * @param requiredPurpose  if non-null, the token {@code purpose} claim must equal this
 */
public record TrustExpectations(Set<String> allowedIssuers, String requiredAudience, String requiredPurpose) {

    /** No claim expectations — cryptographic verification only. */
    public static TrustExpectations none() {
        return new TrustExpectations(Set.of(), null, null);
    }

    public static TrustExpectations builder() {
        return none();
    }

    public TrustExpectations withIssuers(String... issuers) {
        return new TrustExpectations(Set.of(issuers), requiredAudience, requiredPurpose);
    }

    public TrustExpectations withAudience(String audience) {
        return new TrustExpectations(allowedIssuers, audience, requiredPurpose);
    }

    public TrustExpectations withPurpose(String purpose) {
        return new TrustExpectations(allowedIssuers, requiredAudience, purpose);
    }
}
