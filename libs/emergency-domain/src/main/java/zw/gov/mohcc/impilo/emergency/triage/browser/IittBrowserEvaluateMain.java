package zw.gov.mohcc.impilo.emergency.triage.browser;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import zw.gov.mohcc.impilo.emergency.triage.IittCriterion;
import zw.gov.mohcc.impilo.emergency.triage.TriageFacts;
import zw.gov.mohcc.impilo.emergency.triage.TriageResult;
import zw.gov.mohcc.impilo.emergency.triage.WhoIittEngine;

/**
 * TeaVM entry that exports {@code window.__IITT_ENGINE__.evaluate(factsJson)} for the offline
 * Tier-A UI. Clinical truth stays in {@link WhoIittEngine}; TypeScript only loads this bundle and
 * renders the returned priority / unassessed list.
 *
 * <p>Facts JSON shape (all fields optional except ageDays for a defensible chart selection):
 * <pre>
 * { "ageDays": 4000, "pregnant": false,
 *   "signs": { "OBSTRUCTED_AIRWAY": true },
 *   "vitals": { "HR": 140, "RR": 28, "TEMP": 38.5, "SPO2": 91, "AVPU": 0 } }
 * </pre>
 */
public final class IittBrowserEvaluateMain {

    private IittBrowserEvaluateMain() {}

    public static void main(String[] args) {
        EvaluateFn fn = new EvaluateFn() {
            @Override
            public String apply(String factsJson) {
                return evaluateJson(factsJson);
            }
        };
        install(fn);
        markReady();
    }

    static String evaluateJson(String factsJson) {
        try {
            TriageFacts facts = parseFacts(factsJson == null ? "" : factsJson);
            TriageResult result = WhoIittEngine.evaluate(facts);
            return toJson(result);
        } catch (IllegalArgumentException ex) {
            return errorJson("NOT_TRIAGEABLE", ex.getMessage());
        } catch (RuntimeException ex) {
            return errorJson("ENGINE_ERROR", ex.getMessage() == null ? "evaluate failed" : ex.getMessage());
        }
    }

    private static TriageFacts parseFacts(String json) {
        TriageFacts.Builder b = TriageFacts.builder();
        Integer ageDays = readInt(json, "ageDays");
        if (ageDays != null) {
            b.ageDays(ageDays);
        }
        Boolean pregnant = readBoolean(json, "pregnant");
        if (pregnant != null) {
            b.pregnant(pregnant);
        }
        // Minimal object walker — TeaVM ADVANCED omits a full JSON library; keep parsing local.
        parseBoolMap(json, "signs", b);
        parseNumMap(json, "vitals", b);
        return b.build();
    }

    private static void parseBoolMap(String json, String key, TriageFacts.Builder b) {
        String block = objectBlock(json, key);
        if (block == null) {
            return;
        }
        int i = 0;
        while (i < block.length()) {
            int kStart = block.indexOf('"', i);
            if (kStart < 0) break;
            int kEnd = block.indexOf('"', kStart + 1);
            if (kEnd < 0) break;
            String name = block.substring(kStart + 1, kEnd);
            int colon = block.indexOf(':', kEnd);
            if (colon < 0) break;
            int v = colon + 1;
            while (v < block.length() && Character.isWhitespace(block.charAt(v))) v++;
            if (block.startsWith("true", v)) {
                b.sign(name, true);
                i = v + 4;
            } else if (block.startsWith("false", v)) {
                b.sign(name, false);
                i = v + 5;
            } else {
                i = kEnd + 1;
            }
        }
    }

    private static void parseNumMap(String json, String key, TriageFacts.Builder b) {
        String block = objectBlock(json, key);
        if (block == null) {
            return;
        }
        int i = 0;
        while (i < block.length()) {
            int kStart = block.indexOf('"', i);
            if (kStart < 0) break;
            int kEnd = block.indexOf('"', kStart + 1);
            if (kEnd < 0) break;
            String name = block.substring(kStart + 1, kEnd);
            int colon = block.indexOf(':', kEnd);
            if (colon < 0) break;
            int v = colon + 1;
            while (v < block.length() && Character.isWhitespace(block.charAt(v))) v++;
            int end = v;
            while (end < block.length() && "0123456789.+-eE".indexOf(block.charAt(end)) >= 0) end++;
            if (end > v) {
                try {
                    b.vital(name, Double.parseDouble(block.substring(v, end)));
                } catch (NumberFormatException ignored) {
                    // skip malformed vital
                }
            }
            i = Math.max(kEnd + 1, end);
        }
    }

    private static String objectBlock(String json, String key) {
        String needle = "\"" + key + "\"";
        int at = json.indexOf(needle);
        if (at < 0) return null;
        int brace = json.indexOf('{', at + needle.length());
        if (brace < 0) return null;
        int depth = 0;
        for (int i = brace; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(brace + 1, i);
                }
            }
        }
        return null;
    }

    private static Integer readInt(String json, String key) {
        String needle = "\"" + key + "\"";
        int at = json.indexOf(needle);
        if (at < 0) return null;
        int colon = json.indexOf(':', at + needle.length());
        if (colon < 0) return null;
        int v = colon + 1;
        while (v < json.length() && Character.isWhitespace(json.charAt(v))) v++;
        int end = v;
        while (end < json.length() && "0123456789+-".indexOf(json.charAt(end)) >= 0) end++;
        if (end == v) return null;
        try {
            return Integer.parseInt(json.substring(v, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean readBoolean(String json, String key) {
        String needle = "\"" + key + "\"";
        int at = json.indexOf(needle);
        if (at < 0) return null;
        int colon = json.indexOf(':', at + needle.length());
        if (colon < 0) return null;
        int v = colon + 1;
        while (v < json.length() && Character.isWhitespace(json.charAt(v))) v++;
        if (json.startsWith("true", v)) return true;
        if (json.startsWith("false", v)) return false;
        return null;
    }

    private static String toJson(TriageResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"priority\":\"").append(result.priority().name()).append('"');
        sb.append(",\"chart\":\"").append(escape(result.chart())).append('"');
        sb.append(",\"destination\":\"").append(escape(result.destination())).append('"');
        sb.append(",\"requiresClinicianReview\":").append(result.requiresClinicianReview());
        sb.append(",\"triggeringFindings\":[");
        boolean first = true;
        for (String finding : result.triggeringFindings()) {
            if (!first) sb.append(',');
            sb.append('"').append(escape(finding)).append('"');
            first = false;
        }
        sb.append("],\"highRiskVitalSigns\":[");
        first = true;
        for (String hr : result.highRiskVitalSigns()) {
            if (!first) sb.append(',');
            sb.append('"').append(escape(hr)).append('"');
            first = false;
        }
        sb.append("],\"unassessedInputs\":[");
        first = true;
        for (String u : result.unassessedInputs()) {
            if (!first) sb.append(',');
            sb.append('"').append(escape(u)).append('"');
            first = false;
        }
        sb.append("],\"triggeringCriteriaCodes\":[");
        first = true;
        for (IittCriterion c : result.triggeringCriteria()) {
            if (!first) sb.append(',');
            sb.append('"').append(escape(c.code())).append('"');
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String errorJson(String code, String message) {
        return "{\"priority\":\"" + code + "\",\"error\":true,\"message\":\"" + escape(message) + "\","
                + "\"chart\":\"\",\"destination\":\"\",\"requiresClinicianReview\":false,"
                + "\"triggeringFindings\":[],\"highRiskVitalSigns\":[],\"unassessedInputs\":[],"
                + "\"triggeringCriteriaCodes\":[]}";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @JSFunctor
    interface EvaluateFn extends JSObject {
        String apply(String factsJson);
    }

    @JSBody(params = { "fn" }, script = ""
            + "window.__IITT_ENGINE__ = window.__IITT_ENGINE__ || {};"
            + "window.__IITT_ENGINE__.fnType = typeof fn;"
            + "window.__IITT_ENGINE__.fnKeys = fn ? Object.keys(fn) : [];"
            + "window.__IITT_ENGINE__.hasApply = !!(fn && fn.apply);"
            + "window.__IITT_ENGINE__.evaluate = function(factsJson) {"
            + "  try {"
            + "    if (typeof fn === 'function') return fn(factsJson);"
            + "    if (fn && typeof fn.apply === 'function') return fn.apply(factsJson);"
            + "    return JSON.stringify({priority:'ENGINE_ERROR',error:true,message:'no callable'});"
            + "  } catch (e) {"
            + "    return JSON.stringify({priority:'ENGINE_ERROR',error:true,message:String(e)});"
            + "  }"
            + "};")
    private static native void install(EvaluateFn fn);

    @JSBody(script = "window.__IITT_ENGINE__ = window.__IITT_ENGINE__ || {}; window.__IITT_ENGINE__.ready = true;")
    private static native void markReady();
}
