package zw.gov.mohcc.impilo.mvumo.surface;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
/**
 * Derives a clinically oriented consent summary surface (flags, actives, refusals) from
 * product consent request rows. Context JSON may carry {@code surfaceHints} (codes, DNR, etc.) for
 * future enrichment; this class also maps known {@code consentType} values to critical flags.
 */
public final class ConsentSummaryDeriver {

    public static final String SEVERITY_CRITICAL = "CRITICAL";
    public static final String SEVERITY_HIGH = "HIGH";
    public static final String SEVERITY_MODERATE = "MODERATE";
    public static final String SEVERITY_INFO = "INFORMATIONAL";

    private static final Set<String> GRANTED = Set.of("GRANTED", "PARTIALLY_GRANTED");
    private static final Set<String> REFUSED = Set.of("REFUSED");
    private static final Set<String> WITHDRAWN = Set.of("WITHDRAWN", "EXPIRED", "SUPERSEDED", "INVALIDATED", "REVOKED_BY_POLICY");

    private final ObjectMapper objectMapper;

    public ConsentSummaryDeriver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> derive(List<Map<String, Object>> requestRows) {
        List<Map<String, Object>> criticalFlags = new ArrayList<>();
        List<Map<String, Object>> activeConsents = new ArrayList<>();
        List<Map<String, Object>> refusals = new ArrayList<>();
        List<Map<String, Object>> withdrawals = new ArrayList<>();
        List<Map<String, Object>> dataSharingRestrictions = new ArrayList<>();
        List<Map<String, Object>> communicationPreferences = new ArrayList<>();
        List<Map<String, Object>> treatmentLimitations = new ArrayList<>();
        List<Map<String, Object>> advanceDirectives = new ArrayList<>();
        List<Map<String, Object>> bloodProduct = new ArrayList<>();
        List<Map<String, Object>> missingRequired = new ArrayList<>();
        String capacityStatus = null;
        for (Map<String, Object> row : requestRows) {
            String state = str(row.get("state"));
            String type = str(row.get("consentType"));
            String template = str(row.get("templateId"));
            JsonNode ctx = parseContext(str(row.get("contextJson")));
            // Merge structured hints
            if (ctx.has("capacityStatus") && !ctx.get("capacityStatus").asText("").isBlank()) {
                capacityStatus = ctx.get("capacityStatus").asText();
            }
            mapHintsFromContext(ctx, type, state, row, criticalFlags, treatmentLimitations, advanceDirectives, bloodProduct, communicationPreferences, dataSharingRestrictions);
            if (type.toUpperCase(Locale.ROOT).contains("RESEARCH")
                    && state.contains("PENDING")
                    && ctx.path("requiredFor").asText("").contains("EXPORT")) {
                missingRequired.add(simpleItem(type, "Research consent may be required for this workflow", state, row));
            }
            if (GRANTED.contains(state)) {
                activeConsents.add(consentItem(type, state, row, template, ctx, "granted"));
            } else if (REFUSED.contains(state)) {
                refusals.add(consentItem(type, state, row, template, ctx, "refused"));
            } else if (WITHDRAWN.contains(state)) {
                withdrawals.add(consentItem(type, state, row, template, ctx, "withdrawn"));
            }
            heuristicsFromConsentType(type, state, row, criticalFlags, treatmentLimitations, bloodProduct, communicationPreferences, dataSharingRestrictions, advanceDirectives);
        }
        dedupeFlags(criticalFlags);
        var out = new HashMap<String, Object>();
        out.put("criticalFlags", criticalFlags);
        out.put("advanceDirectives", advanceDirectives);
        out.put("treatmentLimitations", treatmentLimitations);
        out.put("bloodProductRestrictions", bloodProduct);
        out.put("activeConsents", activeConsents);
        out.put("missingRequiredConsents", missingRequired);
        out.put("refusals", refusals);
        out.put("withdrawals", withdrawals);
        out.put("dataSharingRestrictions", dataSharingRestrictions);
        out.put("communicationPreferences", communicationPreferences);
        out.put("proxyGuardianInfo", new ArrayList<>());
        out.put("capacityStatus", capacityStatus);
        out.put("lastReviewedAt", null);
        out.put("requiresReview", !missingRequired.isEmpty());
        return out;
    }

    private void heuristicsFromConsentType(
            String type,
            String state,
            Map<String, Object> row,
            List<Map<String, Object>> criticalFlags,
            List<Map<String, Object>> treatmentLimitations,
            List<Map<String, Object>> bloodProduct,
            List<Map<String, Object>> communication,
            List<Map<String, Object>> dataSharing,
            List<Map<String, Object>> advanceDirectives) {
        if (!GRANTED.contains(state) && !REFUSED.contains(state) && !WITHDRAWN.contains(state)) {
            return;
        }
        String u = type.toUpperCase(Locale.ROOT);
        if (u.contains("RESUSCITATION") || u.contains("DNR") || u.contains("DO_NOT")) {
            criticalFlags.add(flag("DNR", "DNR / resuscitation preference", SEVERITY_CRITICAL, row, "mvumo"));
        }
        if (u.contains("BLOOD") || u.contains("TRANSFUSION")) {
            (REFUSED.contains(state) ? treatmentLimitations : bloodProduct)
                    .add(simpleItem(
                            type,
                            REFUSED.contains(state) ? "No blood / transfusion (refused)" : "Blood product restriction",
                            state,
                            row));
        }
        if (u.contains("TELEMEDICINE") && GRANTED.contains(state)) {
            // informational in banner unless in tele workflow — caller filters
            dataSharing.add(simpleItem(type, "Telemedicine / digital consent on file", state, row));
        }
        if (u.contains("ADVANCE") || u.contains("LIVING_WILL") || u.contains("DIRECTIVE")) {
            advanceDirectives.add(simpleItem(type, "Advance directive on file", state, row));
        }
        if (u.contains("SMS") || u.contains("COMMUNICATION") || u.contains("MESSAGING")) {
            communication.add(simpleItem(type, type, state, row));
        }
    }

    private void mapHintsFromContext(
            JsonNode ctx,
            String type,
            String state,
            Map<String, Object> row,
            List<Map<String, Object>> critical,
            List<Map<String, Object>> treatLim,
            List<Map<String, Object>> adv,
            List<Map<String, Object>> blood,
            List<Map<String, Object>> comm,
            List<Map<String, Object>> data) {
        JsonNode hints = ctx.path("surfaceHints");
        if (hints.isArray()) {
            for (JsonNode h : hints) {
                addFlagFromHint(h, row, state, critical, treatLim, adv, blood, comm, data);
            }
        }
        if (ctx.path("dnr").asBoolean(false) || "DNR".equals(ctx.path("code").asText(""))) {
            critical.add(flag("DNR", "DNR (context)", SEVERITY_CRITICAL, row, "mvumo.context"));
        }
        if (ctx.path("noTransfusion").asBoolean(false) || "NO_BLOOD".equals(ctx.path("code").asText(""))) {
            blood.add(simpleItem(type, "No transfusion (context)", state, row));
        }
        if (ctx.path("interpreterRequired").asBoolean(false)) {
            comm.add(simpleItem(type, "Interpreter required", state, row));
        }
    }

    private void addFlagFromHint(
            JsonNode h,
            Map<String, Object> row,
            String state,
            List<Map<String, Object>> critical,
            List<Map<String, Object>> treatLim,
            List<Map<String, Object>> adv,
            List<Map<String, Object>> blood,
            List<Map<String, Object>> comm,
            List<Map<String, Object>> data) {
        if (h == null || h.isNull()) {
            return;
        }
        String code = h.path("code").asText("");
        String sev = h.path("severity").asText(SEVERITY_MODERATE);
        String label = h.path("label").asText(code);
        String family = h.path("family").asText("CRITICAL");
        if ("BLOOD".equals(family) || "NO_BLOOD".equalsIgnoreCase(code)) {
            blood.add(simpleItem(code, label, state, row));
        } else if ("COMMUNICATION".equals(family)) {
            comm.add(simpleItem(code, label, state, row));
        } else if ("DATA".equals(family)) {
            data.add(simpleItem(code, label, state, row));
        } else if ("TREATMENT".equals(family)) {
            treatLim.add(simpleItem(code, label, state, row));
        } else if ("ADVANCE".equals(family)) {
            adv.add(simpleItem(code, label, state, row));
        } else {
            critical.add(flag(code, label, sev, row, "mvumo.surfaceHint"));
        }
    }

    private static Map<String, Object> flag(String code, String label, String severity, Map<String, Object> row, String source) {
        var m = new HashMap<String, Object>();
        m.put("id", str(row.get("id")));
        m.put("code", code);
        m.put("label", label);
        m.put("severity", severity);
        m.put("source", source);
        m.put("effectiveDate", firstNonNull(row, "createdAt", "updatedAt"));
        m.put("expiryDate", row.get("expiresAt"));
        m.put("state", str(row.get("state")));
        m.put("detailHref", null);
        return m;
    }

    private static Map<String, Object> simpleItem(String type, String title, String state, Map<String, Object> row) {
        var m = new HashMap<String, Object>();
        m.put("title", title);
        m.put("consentType", type);
        m.put("state", state);
        m.put("requestId", str(row.get("id")));
        m.put("at", firstNonNull(row, "createdAt", "updatedAt"));
        m.put("proofRef", str(row.get("proofRef")));
        return m;
    }

    private static Map<String, Object> consentItem(String type, String state, Map<String, Object> row, String template, JsonNode ctx, String bucket) {
        var m = new HashMap<String, Object>();
        m.put("consentType", type);
        m.put("state", state);
        m.put("requestId", str(row.get("id")));
        m.put("templateId", template);
        m.put("tshepoConsentId", str(row.get("tshepoConsentId")));
        m.put("summary", ctx.path("summary").asText(""));
        m.put("bucket", bucket);
        m.put("at", firstNonNull(row, "updatedAt", "createdAt"));
        return m;
    }

    private static String firstNonNull(Map<String, Object> row, String a, String b) {
        Object x = row.get(a);
        if (x != null) {
            return x.toString();
        }
        Object y = row.get(b);
        return y != null ? y.toString() : null;
    }

    private void dedupeFlags(List<Map<String, Object>> flags) {
        var seen = new java.util.HashSet<String>();
        flags.removeIf(
                f -> {
                    String k = f.get("code") + "|" + f.get("label");
                    return !seen.add(k);
                });
    }

    private JsonNode parseContext(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
