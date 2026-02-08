package zw.gov.mohcc.impilo.mushex.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReconImportLine(
        String statementRef,
        LocalDate statementDate,
        BigDecimal amount,
        String currency,
        String counterparty
) {}
