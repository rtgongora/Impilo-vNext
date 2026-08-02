package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Identity proofing strength (IAL / LoA). Deliberately separate from {@link AuthenticationAssurance}.
 *
 * <p>{@code X-Assurance-Level} remains identity-proofing only — never authentication AAL.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdentityAssurance(
        String contractVersion,
        int ial,
        String proofingMethod,
        String evidenceReference
) {
    public IdentityAssurance {
        TrustContractVersion.requireV1(contractVersion);
        if (ial < 0 || ial > 4) {
            throw new IllegalArgumentException("ial must be between 0 and 4");
        }
    }

    public static IdentityAssurance of(int ial, String proofingMethod, String evidenceReference) {
        return new IdentityAssurance(TrustContractVersion.V1, ial, proofingMethod, evidenceReference);
    }

    public static IdentityAssurance unknown() {
        return of(0, null, null);
    }
}
