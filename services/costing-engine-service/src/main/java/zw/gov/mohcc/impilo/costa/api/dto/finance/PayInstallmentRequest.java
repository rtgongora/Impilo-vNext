package zw.gov.mohcc.impilo.costa.api.dto.finance;

import java.math.BigDecimal;

public record PayInstallmentRequest(
        BigDecimal amount
) {}
