package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** Assurance attributes of a workload credential (not a human session). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkloadAssurance(
        String contractVersion,
        String credentialType,
        Instant issuedAt,
        Instant expiresAt,
        boolean mTlsBound,
        String issuer
) {
    public WorkloadAssurance {
        TrustContractVersion.requireV1(contractVersion);
        if (credentialType == null || credentialType.isBlank()) {
            throw new IllegalArgumentException("credentialType is required");
        }
        if (expiresAt != null && issuedAt != null && expiresAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must not precede issuedAt");
        }
    }

    public static WorkloadAssurance of(
            String credentialType,
            Instant issuedAt,
            Instant expiresAt,
            boolean mTlsBound,
            String issuer) {
        return new WorkloadAssurance(
                TrustContractVersion.V1, credentialType, issuedAt, expiresAt, mTlsBound, issuer);
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now != null && expiresAt.isBefore(now);
    }
}
