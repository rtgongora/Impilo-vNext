package zw.gov.mohcc.impilo.costa.api.dto;

import zw.gov.mohcc.impilo.costa.domain.enums.BillType;

public record BillDraftRequest(
        String encounterId,
        String msikaOrderId,
        BillType billType
) {}
