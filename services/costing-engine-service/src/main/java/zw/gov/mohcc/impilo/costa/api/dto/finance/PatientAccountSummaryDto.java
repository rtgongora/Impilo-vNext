package zw.gov.mohcc.impilo.costa.api.dto.finance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PatientAccountSummaryDto(
        UUID accountId,
        String patientCpid,
        BigDecimal totalBilled,
        BigDecimal totalPaid,
        BigDecimal totalInsurer,
        BigDecimal totalOutstanding,
        BigDecimal totalWriteoff,
        String currency,
        OffsetDateTime lastActivity
) {}
