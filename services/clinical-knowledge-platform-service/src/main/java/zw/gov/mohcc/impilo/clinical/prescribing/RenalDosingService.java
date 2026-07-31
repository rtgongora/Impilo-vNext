package zw.gov.mohcc.impilo.clinical.prescribing;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.medicine.pharm.RenalDosingAdvisor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serves renal-dosing awareness from {@link RenalDosingAdvisor} — one implementation, no duplicate
 * arithmetic in the BFF or browser.
 */
@Service
public class RenalDosingService {

    public Map<String, Object> advise(Double egfr, String drugClass) {
        RenalDosingAdvisor.Advice advice = RenalDosingAdvisor.advise(egfr, drugClass);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("level", advice.level().name());
        data.put("message", advice.message());
        data.put("computed", advice.computed());
        data.put("drug_class", advice.drugClass());
        if (advice.drugClassDisplay() != null) {
            data.put("drug_class_display", advice.drugClassDisplay());
        }
        if (advice.egfr() != null) {
            data.put("egfr", advice.egfr());
        }
        data.put("content_version", advice.contentVersion());
        return data;
    }
}
