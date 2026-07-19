package zw.gov.mohcc.impilo.coverage.api.dto;

import zw.gov.mohcc.impilo.coverage.domain.PayerEntity;

import java.time.LocalDate;
import java.util.UUID;

public record PayerResponse(
        UUID id,
        String payerCode,
        String legalName,
        String tradingName,
        String organisationType,
        String status,
        String integrationMethod,
        String settlementReference,
        String regulatoryStatus,
        String sourceAuthority,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
    public static PayerResponse of(PayerEntity p) {
        return new PayerResponse(p.getId(), p.getPayerCode(), p.getLegalName(), p.getTradingName(),
                p.getOrganisationType(), p.getStatus(), p.getIntegrationMethod(), p.getSettlementReference(),
                p.getRegulatoryStatus(), p.getSourceAuthority(), p.getEffectiveFrom(), p.getEffectiveTo());
    }
}
