package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Eligibility v2 request/response DTOs (spec §12.2/§12.3). */
public final class EligibilityV2Dtos {

    private EligibilityV2Dtos() {}

    public record EligibilityV2Request(
            @NotNull UUID coverageId,
            String patientRef,
            String serviceCode,
            String benefitCode,
            String facilityId,
            String providerId,
            BigDecimal estimatedAmount,
            Boolean issueToken) {}

    public record BenefitLine(
            String benefitCode, String category, BigDecimal coveragePercent, BigDecimal copay,
            BigDecimal limit, BigDecimal used, BigDecimal reserved, BigDecimal remaining,
            boolean requiresAuthorisation, boolean requiresReferral, String networkRequirement) {}

    public record EligibilityV2Response(
            UUID eligibilityId,
            String status,               // ELIGIBLE, PARTIALLY_ELIGIBLE, INELIGIBLE, PENDING, UNKNOWN, ...
            UUID coverageId,
            String memberCpid,
            String membershipStatus,
            String verificationStatus,
            UUID planId,
            String planCode,
            UUID planVersionId,
            String networkStatus,
            boolean referralRequired,
            boolean authorisationRequired,
            List<BenefitLine> benefits,
            List<String> reasonCodes,
            String rulesetVersion,
            OffsetDateTime validUntil,
            String signedToken,          // present when issueToken=true and eligible
            OffsetDateTime tokenExpiresAt) {}

    public record VerifyTokenRequest(@NotNull String token) {}

    public record VerifyTokenResponse(boolean valid, String reason, java.util.Map<String, Object> claims) {}
}
