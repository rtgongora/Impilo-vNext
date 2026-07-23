package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.coverage.domain.AuthorisationEntity;
import zw.gov.mohcc.impilo.coverage.domain.AuthorisationLineEntity;
import zw.gov.mohcc.impilo.coverage.domain.LiabilityEstimateEntity;
import zw.gov.mohcc.impilo.coverage.domain.ReferralEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** W2 request/response DTOs (authorisations §17, referrals §16, liability §18). */
public final class AuthorisationDtos {

    private AuthorisationDtos() {}

    // ---- Authorisations ----
    public record LineInputDto(String benefitCode, String serviceCode, String description,
                               BigDecimal quantityRequested, BigDecimal estimatedAmount) {}

    public record CreateAuthorisationRequest(
            @NotNull UUID coverageId,
            String authType,
            String requestingProviderId,
            String facilityId,
            UUID referralId,
            String diagnosisSystem,
            String diagnosisCode,
            String clinicalJustification,
            String urgency,
            LocalDate proposedServiceDate,
            List<LineInputDto> lines) {}

    public record TransitionAuthorisationRequest(String reviewerId, String note) {}

    public record LineDecisionDto(UUID lineId, String outcome, BigDecimal quantityApproved,
                                  BigDecimal approvedAmount, String reason) {}

    public record DecideAuthorisationRequest(
            List<LineDecisionDto> lines, String reviewerId,
            LocalDate validityFrom, LocalDate validityTo, String conditions) {}

    public record AuthorisationLineView(UUID id, String benefitCode, String serviceCode, String description,
                                        BigDecimal quantityRequested, BigDecimal quantityApproved,
                                        BigDecimal estimatedAmount, BigDecimal approvedAmount,
                                        String lineStatus, String reason) {
        public static AuthorisationLineView of(AuthorisationLineEntity l) {
            return new AuthorisationLineView(l.getId(), l.getBenefitCode(), l.getServiceCode(), l.getDescription(),
                    l.getQuantityRequested(), l.getQuantityApproved(), l.getEstimatedAmount(), l.getApprovedAmount(),
                    l.getLineStatus(), l.getReason());
        }
    }

    public record AuthorisationView(UUID id, UUID coverageId, String memberCpid, String authType, String status,
                                    String urgency, String requestingProviderId, String facilityId, UUID referralId,
                                    BigDecimal estimatedAmount, String reviewerId, OffsetDateTime decisionAt,
                                    LocalDate validityFrom, LocalDate validityTo, String denialReason,
                                    String informationRequest, List<AuthorisationLineView> lines) {
        public static AuthorisationView of(AuthorisationEntity a, List<AuthorisationLineEntity> lines) {
            return new AuthorisationView(a.getId(), a.getCoverageId(), a.getMemberCpid(), a.getAuthType(),
                    a.getStatus(), a.getUrgency(), a.getRequestingProviderId(), a.getFacilityId(), a.getReferralId(),
                    a.getEstimatedAmount(), a.getReviewerId(), a.getDecisionAt(), a.getValidityFrom(),
                    a.getValidityTo(), a.getDenialReason(), a.getInformationRequest(),
                    lines == null ? List.of() : lines.stream().map(AuthorisationLineView::of).toList());
        }
    }

    // ---- Referrals ----
    public record CreateReferralRequest(
            UUID coverageId,
            String memberCpid,
            String referringProviderId,
            String referredProviderId,
            String referredFacilityId,
            String clinicalReferralRef,
            String scope,
            String reason,
            Integer permittedVisits,
            LocalDate validTo) {}

    public record ReferralView(UUID id, UUID coverageId, String memberCpid, String referringProviderId,
                               String referredProviderId, String referredFacilityId, String scope, String reason,
                               int permittedVisits, int visitsUsed, String status, LocalDate validFrom, LocalDate validTo) {
        public static ReferralView of(ReferralEntity r) {
            return new ReferralView(r.getId(), r.getCoverageId(), r.getMemberCpid(), r.getReferringProviderId(),
                    r.getReferredProviderId(), r.getReferredFacilityId(), r.getScope(), r.getReason(),
                    r.getPermittedVisits(), r.getVisitsUsed(), r.getStatus(), r.getValidFrom(), r.getValidTo());
        }
    }

    public record ReferralValidityView(boolean valid, String reason, int visitsRemaining) {}

    // ---- Liability estimate ----
    public record EstimateRequest(
            @NotNull UUID coverageId,
            String benefitCode,
            @NotNull BigDecimal standardCharge,
            String facilityId) {}

    public record EstimateView(UUID id, UUID coverageId, String memberCpid, String benefitCode,
                               BigDecimal standardCharge, BigDecimal allowedAmount, BigDecimal payerEstimate,
                               BigDecimal copay, BigDecimal coinsurance, BigDecimal nonCovered,
                               BigDecimal patientResponsibility, String currency, String assumptions,
                               String rulesetVersion, OffsetDateTime expiresAt,
                               /* OF-B8 §10.6 — benefit PA requirement at estimate time */
                               boolean requiresAuthorisation) {
        public static EstimateView of(LiabilityEstimateEntity e) {
            return new EstimateView(e.getId(), e.getCoverageId(), e.getMemberCpid(), e.getBenefitCode(),
                    e.getStandardCharge(), e.getAllowedAmount(), e.getPayerEstimate(), e.getCopay(),
                    e.getCoinsurance(), e.getNonCovered(), e.getPatientResponsibility(), e.getCurrency(),
                    e.getAssumptions(), e.getRulesetVersion(), e.getExpiresAt(),
                    e.isRequiresAuthorisation());
        }
    }
}
