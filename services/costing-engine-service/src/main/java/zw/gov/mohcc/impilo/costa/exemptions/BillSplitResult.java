package zw.gov.mohcc.impilo.costa.exemptions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Result of bill splitting across parties (patient, insurer, subsidy, write-off).
 */
public record BillSplitResult(
        BigDecimal totalPayable,
        BigDecimal patientPayable,
        BigDecimal insurerPayable,
        BigDecimal subsidyPayable,
        BigDecimal writeOff,
        BigDecimal totalDiscount,
        List<PartyAllocation> allocations,
        Map<String, Object> trace
) {
    public record PartyAllocation(
            String partyType,
            String partyRef,
            BigDecimal amount,
            String[] reasonCodes,
            Map<String, Object> trace
    ) {}
}
