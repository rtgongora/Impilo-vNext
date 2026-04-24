package zw.gov.mohcc.impilo.experience.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityHeaderParser;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Merges Tshepo/gateway visibility signals into intelligence request {@code context}
 * so downstream orchestration can shape outputs without bypassing PEP decisions.
 */
public final class IntelligenceHttpContext {

    private IntelligenceHttpContext() {
    }

    /**
     * Normalises {@code body.context} and injects {@code effectiveVisibility} when headers resolve.
     */
    public static void mergeEffectiveVisibility(HttpServletRequest request, ObjectMapper mapper, Map<String, Object> body) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        Object raw = body.get("context");
        if (raw instanceof Map<?, ?> rm) {
            for (Map.Entry<?, ?> e : rm.entrySet()) {
                ctx.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        VisibilityProfile p = VisibilityHeaderParser.resolve(request, mapper);
        if (p != null) {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("tier", p.visibilityTier());
            ev.put("aggregateOnly", p.aggregateOnly());
            ev.put("clinicalAccess", p.clinicalAccess());
            ev.put("piiAccess", p.piiAccess());
            ev.put("resourceSensitivityClass", p.resourceSensitivityClass());
            ev.put("drillDownAllowed", p.drillDownAllowed());
            ctx.put("effectiveVisibility", ev);
            ctx.putIfAbsent("visibilityTier", p.visibilityTier());
        }
        body.put("context", ctx);
    }

    /**
     * Context map for shell-suggest (visibility only, no client-supplied context).
     */
    public static Map<String, Object> visibilityContext(HttpServletRequest request, ObjectMapper mapper) {
        Map<String, Object> body = new LinkedHashMap<>();
        mergeEffectiveVisibility(request, mapper, body);
        @SuppressWarnings("unchecked")
        Map<String, Object> ctx = (Map<String, Object>) body.get("context");
        return ctx != null ? ctx : Map.of();
    }
}
