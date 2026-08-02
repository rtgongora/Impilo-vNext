package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** Appointment, licence, mandate or role binding — a role alone is not sufficient authority. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthorityBinding(
        String contractVersion,
        String authorityType,
        String authorityId,
        List<String> roles,
        String licenceId,
        String appointmentId,
        String mandateId,
        Instant expiresAt,
        boolean suspended
) {
    public AuthorityBinding {
        TrustContractVersion.requireV1(contractVersion);
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public static AuthorityBinding of(
            String authorityType,
            String authorityId,
            List<String> roles,
            String licenceId,
            String appointmentId,
            String mandateId,
            Instant expiresAt,
            boolean suspended) {
        return new AuthorityBinding(
                TrustContractVersion.V1,
                authorityType,
                authorityId,
                roles,
                licenceId,
                appointmentId,
                mandateId,
                expiresAt,
                suspended);
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now != null && expiresAt.isBefore(now);
    }
}
