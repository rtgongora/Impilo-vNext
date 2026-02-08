package zw.gov.mohcc.impilo.costa.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.costa.domain.entity.TariffEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;
import zw.gov.mohcc.impilo.costa.domain.repository.TariffRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Tariff-based costing engine.
 * Looks up the applicable tariff by code, facility category, and patient category.
 * Uses the most specific match: (tariffCode + facilityCategory + patientCategory)
 * falls back to (tariffCode + facilityCategory) then (tariffCode).
 */
@Component
public class TariffCostEngine implements CostEngine {

    private static final Logger log = LoggerFactory.getLogger(TariffCostEngine.class);

    private final TariffRepository tariffRepository;

    public TariffCostEngine(TariffRepository tariffRepository) {
        this.tariffRepository = tariffRepository;
    }

    @Override
    public CostMethodType getMethodType() {
        return CostMethodType.TARIFF;
    }

    @Override
    public CostResult compute(UUID tenantId, UUID facilityId, String msikaCode,
                              BigDecimal qty, Map<String, Object> context) {
        LocalDate asOf = LocalDate.now();
        String facilityCategory = (String) context.getOrDefault("facility_category", null);
        String patientCategory = (String) context.getOrDefault("patient_category", null);

        // Try to find tariff by msika code first, then by tariff code
        List<TariffEntity> tariffs = tariffRepository.findByMsikaCode(tenantId, msikaCode, asOf);
        if (tariffs.isEmpty()) {
            tariffs = tariffRepository.findEffectiveTariff(tenantId, msikaCode, asOf);
        }

        if (tariffs.isEmpty()) {
            log.warn("No tariff found for tenant={}, code={}", tenantId, msikaCode);
            return CostResult.zero(CostMethodType.TARIFF, "No tariff configured for code: " + msikaCode);
        }

        // Find best match by specificity
        TariffEntity bestMatch = findBestMatch(tariffs, facilityCategory, patientCategory);
        BigDecimal unitPrice = bestMatch.getPrice();

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("cost_method", "TARIFF");
        trace.put("tariff_id", bestMatch.getId());
        trace.put("tariff_code", bestMatch.getTariffCode());
        trace.put("msika_code", bestMatch.getMsikaCode());
        trace.put("description", bestMatch.getDescription());
        trace.put("unit_price", unitPrice);
        trace.put("currency", bestMatch.getCurrency());
        trace.put("facility_category_match", bestMatch.getFacilityCategory());
        trace.put("patient_category_match", bestMatch.getPatientCategory());
        trace.put("effective_from", bestMatch.getEffectiveFrom().toString());
        trace.put("version", bestMatch.getVersion());
        trace.put("qty", qty);
        trace.put("total", unitPrice.multiply(qty));

        return CostResult.of(unitPrice, qty, CostMethodType.TARIFF,
                "TARIFF:" + bestMatch.getTariffCode(), trace);
    }

    private TariffEntity findBestMatch(List<TariffEntity> tariffs,
                                       String facilityCategory,
                                       String patientCategory) {
        // Score each tariff: more specific match = higher score
        TariffEntity best = tariffs.get(0);
        int bestScore = 0;

        for (TariffEntity t : tariffs) {
            int score = 0;
            if (facilityCategory != null && facilityCategory.equals(t.getFacilityCategory())) {
                score += 2;
            }
            if (patientCategory != null && patientCategory.equals(t.getPatientCategory())) {
                score += 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }
}
