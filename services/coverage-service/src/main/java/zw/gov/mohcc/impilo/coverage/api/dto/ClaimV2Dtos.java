package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.coverage.domain.ClaimEntity;
import zw.gov.mohcc.impilo.coverage.domain.ClaimEventEntity;
import zw.gov.mohcc.impilo.coverage.domain.ClaimLineEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Claims v2 request/response DTOs (spec §19-§20). */
public final class ClaimV2Dtos {

    private ClaimV2Dtos() {}

    public record ClaimLineInputDto(String benefitCode, String serviceCode, String description,
                                    BigDecimal quantity, BigDecimal submittedAmount) {}

    public record CreateClaimRequest(
            @NotNull UUID coverageId,
            String claimType,
            String facilityId,
            String providerId,
            String encounterId,
            UUID authorisationId,
            UUID referralId,
            List<ClaimLineInputDto> lines) {}

    public record VoidRequest(String reason) {}

    public record ClaimLineView(UUID id, int lineNumber, String benefitCode, String serviceCode, String description,
                                BigDecimal quantity, BigDecimal submittedAmount, BigDecimal allowedAmount,
                                BigDecimal approvedAmount, BigDecimal deniedAmount, BigDecimal copay,
                                BigDecimal coinsurance, BigDecimal patientResponsibility, String lineStatus,
                                String reasonCodes) {
        public static ClaimLineView of(ClaimLineEntity l) {
            return new ClaimLineView(l.getId(), l.getLineNumber(), l.getBenefitCode(), l.getServiceCode(),
                    l.getDescription(), l.getQuantity(), l.getSubmittedAmount(), l.getAllowedAmount(),
                    l.getApprovedAmount(), l.getDeniedAmount(), l.getCopay(), l.getCoinsurance(),
                    l.getPatientResponsibility(), l.getLineStatus(), l.getReasonCodes());
        }
    }

    public record ClaimView(UUID id, String claimNumber, UUID coverageId, String memberCpid, String claimType,
                            String status, String facilityId, String providerId, UUID authorisationId,
                            BigDecimal totalAmount, BigDecimal allowedAmount, BigDecimal approvedAmount,
                            BigDecimal deniedAmount, BigDecimal patientResponsibility, String reasonCodes,
                            String rulesetVersion, String scrubResult, String settlementRef,
                            UUID replacesClaimId, UUID replacedByClaimId, boolean reversed,
                            OffsetDateTime submittedAt, OffsetDateTime adjudicatedAt, List<ClaimLineView> lines) {
        public static ClaimView of(ClaimEntity c, List<ClaimLineEntity> lines) {
            return new ClaimView(c.getId(), c.getClaimNumber(), c.getCoverageId(), c.getMemberCpid(), c.getClaimType(),
                    c.getStatus(), c.getFacilityId(), c.getProviderId(), c.getAuthorisationId(), c.getTotalAmount(),
                    c.getAllowedAmount(), c.getApprovedAmount(), c.getDeniedAmount(), c.getPatientResponsibility(),
                    c.getReasonCodes(), c.getRulesetVersion(), c.getScrubResult(), c.getSettlementRef(),
                    c.getReplacesClaimId(), c.getReplacedByClaimId(), c.isReversed(),
                    c.getSubmittedAt(), c.getAdjudicatedAt(),
                    lines == null ? List.of() : lines.stream().map(ClaimLineView::of).toList());
        }
    }

    public record ScrubView(boolean pass, List<String> blocking, List<String> warnings) {}

    public record ClaimEventView(String eventType, String fromStatus, String toStatus, String actor,
                                 String reason, OffsetDateTime occurredAt) {
        public static ClaimEventView of(ClaimEventEntity e) {
            return new ClaimEventView(e.getEventType(), e.getFromStatus(), e.getToStatus(), e.getActor(),
                    e.getReason(), e.getOccurredAt());
        }
    }

    /** Citizen-facing Explanation of Benefits (spec §20.1) — plain-language line detail. */
    public record EobLine(String service, BigDecimal charged, BigDecimal allowed, BigDecimal paidByPayer,
                          BigDecimal notCovered, BigDecimal yourResponsibility, String outcome, String explanation) {}

    public record EobView(String claimNumber, String facilityId, String providerId, String status,
                          BigDecimal totalCharged, BigDecimal totalPaidByPayer, BigDecimal totalYourResponsibility,
                          OffsetDateTime dateOfService, List<EobLine> lines, String appealRights) {}
}
