package zw.gov.mohcc.impilo.costa.api.dto.finance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PatientTransactionRowDto(
        UUID txnId,
        String txnType,
        String description,
        UUID facilityId,
        String encounterId,
        String billId,
        BigDecimal grossAmount,
        BigDecimal insurerAmount,
        BigDecimal patientAmount,
        String paymentMethod,
        String paymentRef,
        OffsetDateTime txnDate,
        BigDecimal runningBalance
) {}
