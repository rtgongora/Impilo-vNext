package zw.gov.mohcc.impilo.costa.api.dto.finance;

import java.math.BigDecimal;
import java.util.UUID;

public record OutstandingBillDto(
        String billId,
        UUID facilityId,
        String encounterId,
        BigDecimal patientPayable,
        BigDecimal totalPaidOnBill,
        BigDecimal balanceDue,
        String currency
) {}
