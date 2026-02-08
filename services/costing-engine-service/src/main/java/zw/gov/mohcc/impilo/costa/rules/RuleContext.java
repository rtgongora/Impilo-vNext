package zw.gov.mohcc.impilo.costa.rules;

import zw.gov.mohcc.impilo.costa.domain.enums.BillLineKind;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Context passed to the charging rule engine for evaluation.
 */
public record RuleContext(
        UUID tenantId,
        UUID facilityId,
        String facilityCategory,
        String patientCategory,
        String msikaCode,
        BillLineKind kind,
        BigDecimal qty,
        BigDecimal costAmount,
        String encounterType,
        LocalDateTime serviceTime,
        boolean isEmergency,
        boolean isAdmitted,
        Map<String, Object> extras
) {
    public String getExtra(String key) {
        Object v = extras.get(key);
        return v != null ? v.toString() : null;
    }
}
