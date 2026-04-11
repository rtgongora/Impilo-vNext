package zw.gov.mohcc.impilo.clinical.nudge;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.rules.ClinicalRulesEngine;
import zw.gov.mohcc.impilo.clinical.rules.model.ClinicalEvaluationContext;
import zw.gov.mohcc.impilo.clinical.rules.model.RuleAlert;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NudgeEvaluationService {

    private final ClinicalRulesEngine rulesEngine;

    public NudgeEvaluationService(ClinicalRulesEngine rulesEngine) {
        this.rulesEngine = rulesEngine;
    }

    public Map<String, Object> evaluate(Map<String, Object> context) {
        List<RuleAlert> alerts = rulesEngine.evaluate(ClinicalEvaluationContext.fromMap(context));
        return Map.of(
                "nudges", alerts.stream().map(RuleAlert::toMap).collect(Collectors.toList()),
                "count", alerts.size()
        );
    }
}
