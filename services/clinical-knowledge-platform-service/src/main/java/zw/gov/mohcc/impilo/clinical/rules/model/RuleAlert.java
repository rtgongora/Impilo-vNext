package zw.gov.mohcc.impilo.clinical.rules.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record RuleAlert(
        String code,
        String severity,
        String message,
        String explanation,
        boolean interruptive,
        boolean overrideAllowed
) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("severity", severity);
        m.put("message", message);
        m.put("explanation", explanation);
        m.put("interruptive", interruptive);
        m.put("override_allowed", overrideAllowed);
        return m;
    }
}
