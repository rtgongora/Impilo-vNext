package zw.gov.mohcc.impilo.rules.api.dto;

import java.util.List;

public record EvaluateResponse(
        List<DecisionResult> decisions
) {
}
