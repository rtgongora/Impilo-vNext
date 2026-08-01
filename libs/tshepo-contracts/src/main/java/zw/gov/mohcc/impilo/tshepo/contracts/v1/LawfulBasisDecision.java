package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Lawful-basis evaluation result. Not interchangeable with {@link ConsentDecision}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LawfulBasisDecision(
        String contractVersion,
        boolean permitted,
        LawfulBasisType basisType,
        String basisReference,
        String reasonCode,
        String subjectRef,
        String purpose
) {
    public LawfulBasisDecision {
        TrustContractVersion.requireV1(contractVersion);
        if (basisType == null) {
            throw new IllegalArgumentException("basisType is required");
        }
        if (basisType == LawfulBasisType.EXPLICIT_CONSENT) {
            throw new IllegalArgumentException(
                    "EXPLICIT_CONSENT must be modelled as ConsentDecision, not LawfulBasisDecision");
        }
    }

    public static LawfulBasisDecision permit(
            LawfulBasisType basisType, String basisReference, String subjectRef, String purpose) {
        return new LawfulBasisDecision(
                TrustContractVersion.V1, true, basisType, basisReference, null, subjectRef, purpose);
    }

    public static LawfulBasisDecision deny(
            LawfulBasisType basisType, String reasonCode, String subjectRef, String purpose) {
        return new LawfulBasisDecision(
                TrustContractVersion.V1, false, basisType, null, reasonCode, subjectRef, purpose);
    }
}
