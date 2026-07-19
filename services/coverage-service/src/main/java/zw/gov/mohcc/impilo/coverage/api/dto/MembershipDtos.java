package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Membership lifecycle request/response DTOs (spec §10). */
public final class MembershipDtos {

    private MembershipDtos() {}

    public record VerifyRequest(String source, Boolean verified) {}

    public record TransitionRequest(String reason) {}

    public record AddDependantRequest(
            @NotBlank String dependantClientId,
            @NotBlank String relationship,
            String memberNumber) {}

    public record MembershipView(
            UUID id,
            String clientId,
            UUID planId,
            String status,
            String verificationStatus,
            OffsetDateTime verificationDate,
            String verificationSource,
            int coveragePriority,
            String memberCategory,
            String relationship,
            UUID principalMemberId,
            String terminationReason,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
        public static MembershipView of(MemberCoverageEntity m) {
            return new MembershipView(m.getId(), m.getClientId(), m.getPlanId(), m.getStatus(),
                    m.getVerificationStatus(), m.getVerificationDate(), m.getVerificationSource(),
                    m.getCoveragePriority(), m.getMemberCategory(), m.getRelationship(),
                    m.getPrincipalMemberId(), m.getTerminationReason(), m.getEffectiveFrom(), m.getEffectiveTo());
        }
    }
}
