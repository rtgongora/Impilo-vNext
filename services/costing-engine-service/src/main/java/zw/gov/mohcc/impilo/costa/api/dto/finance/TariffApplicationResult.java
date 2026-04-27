package zw.gov.mohcc.impilo.costa.api.dto.finance;

import zw.gov.mohcc.impilo.costa.domain.enums.BillingTimingMode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured summary of a tariff application / cost preview run (returned alongside raw breakdown maps).
 */
public record TariffApplicationResult(
        long tariffListId,
        String tariffExternalCode,
        BillingTimingMode billingTimingMode,
        String tariffedPrice,
        String patientLiability,
        String payerLiability,
        String exemptedAmount,
        String subsidisedAmount
) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tariff_list_id", tariffListId);
        m.put("tariff_external_code", tariffExternalCode);
        if (billingTimingMode != null) {
            m.put("billing_timing_mode", billingTimingMode.name());
        }
        m.put("tariffed_price", tariffedPrice);
        m.put("patient_liability", patientLiability);
        m.put("payer_liability", payerLiability);
        m.put("exempted_amount", exemptedAmount);
        m.put("subsidised_amount", subsidisedAmount);
        return m;
    }
}
