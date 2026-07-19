package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.coverage.core.BenefitAccumulatorService.BenefitPosition;
import zw.gov.mohcc.impilo.coverage.core.BenefitAccumulatorService.MovementResult;
import zw.gov.mohcc.impilo.coverage.domain.BenefitDefinitionEntity;

import java.math.BigDecimal;
import java.util.UUID;

/** Benefit + accumulator request/response DTOs (spec §13). */
public final class BenefitDtos {

    private BenefitDtos() {}

    public record CreateBenefitRequest(
            @NotBlank String benefitCode,
            @NotBlank String category,
            String description,
            BigDecimal coveragePercent,
            BigDecimal copayAmount,
            Boolean requiresAuthorisation,
            Boolean requiresReferral,
            String networkRequirement,
            BigDecimal annualLimit) {}

    public record BenefitResponse(
            UUID id, UUID planVersionId, String benefitCode, String category, String description,
            BigDecimal coveragePercent, BigDecimal copayAmount, boolean requiresAuthorisation,
            boolean requiresReferral, String networkRequirement) {
        public static BenefitResponse of(BenefitDefinitionEntity b) {
            return new BenefitResponse(b.getId(), b.getPlanVersionId(), b.getBenefitCode(), b.getCategory(),
                    b.getDescription(), b.getCoveragePercent(), b.getCopayAmount(), b.isRequiresAuthorisation(),
                    b.isRequiresReferral(), b.getNetworkRequirement());
        }
    }

    public record BenefitPositionResponse(
            String benefitCode, String category, BigDecimal coveragePercent, BigDecimal copay,
            BigDecimal limit, BigDecimal used, BigDecimal reserved, BigDecimal remaining,
            boolean requiresAuthorisation, boolean requiresReferral, String networkRequirement) {
        public static BenefitPositionResponse of(BenefitPosition p) {
            return new BenefitPositionResponse(p.benefitCode(), p.category(), p.coveragePercent(), p.copay(),
                    p.limit(), p.used(), p.reserved(), p.remaining(), p.requiresAuthorisation(),
                    p.requiresReferral(), p.networkRequirement());
        }
    }

    public record MovementRequest(
            @NotBlank String benefitCode,
            @NotNull BigDecimal amount,
            @NotBlank String reference) {}

    public record MovementResponse(
            UUID accumulatorId, String benefitCode, BigDecimal limit, BigDecimal used,
            BigDecimal reserved, BigDecimal remaining, boolean applied, String reason) {
        public static MovementResponse of(MovementResult r) {
            return new MovementResponse(r.accumulatorId(), r.benefitCode(), r.limit(), r.used(),
                    r.reserved(), r.remaining(), r.applied(), r.reason());
        }
    }
}
