package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Human actor reference — distinct from {@link WorkloadSubject}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HumanSubjectRef(
        String contractVersion,
        String healthId,
        String actorType,
        String providerId
) {
    public HumanSubjectRef {
        TrustContractVersion.requireV1(contractVersion);
        if (healthId == null || healthId.isBlank()) {
            throw new IllegalArgumentException("healthId is required for a human subject");
        }
    }

    public static HumanSubjectRef of(String healthId, String actorType, String providerId) {
        return new HumanSubjectRef(TrustContractVersion.V1, healthId, actorType, providerId);
    }
}
