package zw.gov.mohcc.impilo.clinical.growth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Growth interpretation as knowledge, not as a record.
 *
 * <p>Stateless in the same way as the immunisation forecast: pct-service owns the growth history
 * and the z-scores stamped at the time of measurement, and passes them in. That keeps one system of
 * record for growth, lets the same interpretation run against an offline copy of the same
 * measurements, and — most importantly — means this service can never disagree with the stored
 * score, because it never computes one.</p>
 */
@Service
public class GrowthIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(GrowthIntelligenceService.class);

    static final String CONTENT_PATH = "clinical/paediatric-growth-intelligence.json";

    private final ObjectMapper objectMapper;
    private GrowthIntelligenceContent cached;

    public GrowthIntelligenceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public synchronized GrowthIntelligenceContent content() {
        if (cached == null) {
            cached = load();
        }
        return cached;
    }

    public GrowthIntelligenceEngine.Assessment assess(List<Map<String, Object>> history) {
        List<GrowthIntelligenceEngine.Point> points = new ArrayList<>();
        for (Map<String, Object> row : history == null ? List.<Map<String, Object>>of() : history) {
            points.add(toPoint(row));
        }
        return GrowthIntelligenceEngine.assess(content(), points);
    }

    static GrowthIntelligenceEngine.Point toPoint(Map<String, Object> row) {
        return new GrowthIntelligenceEngine.Point(
                integer(row, "ageDays", "age_days"),
                decimal(row, "weightKg", "weight_kg"),
                decimal(row, "weightForAgeZ", "weight_for_age_z"),
                decimal(row, "lengthHeightForAgeZ", "length_height_for_age_z"),
                decimal(row, "headCircumferenceForAgeZ", "head_circumference_for_age_z"),
                text(row, "statureMode", "stature_mode", "measurement_mode"),
                // pct-service returns flat rows carrying "growth_standard"; the experience BFF
                // reshapes the same stamp as "standard" under derived. Both are accepted so a
                // caller composing from either does not have to rename the field it already holds
                // — and a caller that omits it entirely leaves the engine unable to see a handover
                // it is not told about.
                text(row, "growthStandard", "growth_standard", "standard"),
                integer(row, "postmenstrualAgeWeeks", "postmenstrual_age_weeks"));
    }

    private GrowthIntelligenceContent load() {
        try (InputStream stream = GrowthIntelligenceService.class.getClassLoader()
                .getResourceAsStream(CONTENT_PATH)) {
            if (stream == null) {
                log.error("Growth intelligence content not found on the classpath: {}", CONTENT_PATH);
                return empty();
            }
            JsonNode root = objectMapper.readTree(stream);

            List<GrowthIntelligenceContent.SignalDefinition> signals = new ArrayList<>();
            int rejected = 0;
            for (JsonNode n : root.path("signals")) {
                String code = n.path("code").asText(null);
                String action = n.path("action").asText(null);
                if (code == null || action == null) {
                    // A signal without an action is a notification, and a notification a health
                    // worker cannot act on is noise on a growth chart.
                    log.error("Growth signal rejected: missing code or action");
                    rejected++;
                    continue;
                }
                signals.add(new GrowthIntelligenceContent.SignalDefinition(
                        code,
                        n.path("name").asText(code),
                        n.path("severity").asText("MODERATE"),
                        n.path("priority").asInt(1000),
                        n.hasNonNull("measure") ? n.get("measure").asText() : null,
                        n.hasNonNull("family") ? n.get("family").asText() : null,
                        dec(n, "zDropAtLeast"),
                        dec(n, "zRiseAtLeast"),
                        dec(n, "zChangeMagnitudeAtLeast"),
                        n.hasNonNull("staticWeightMinDays") ? n.get("staticWeightMinDays").asInt() : null,
                        n.path("absoluteWeightLoss").asBoolean(false),
                        n.path("statureModeChanged").asBoolean(false),
                        n.path("standardChanged").asBoolean(false),
                        n.hasNonNull("maxAgeDays") ? n.get("maxAgeDays").asInt() : null,
                        action,
                        n.path("referralRequired").asBoolean(false),
                        n.path("blocksInterpretation").asBoolean(false)));
            }
            signals.sort((a, b) -> Integer.compare(a.priority(), b.priority()));

            List<GrowthIntelligenceContent.TestCase> cases = new ArrayList<>();
            for (JsonNode c : root.path("testCases")) {
                List<Map<String, Object>> pts = objectMapper.convertValue(
                        c.path("points"), objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, Map.class));
                List<String> expect = new ArrayList<>();
                c.path("expectSignals").forEach(e -> expect.add(e.asText()));
                cases.add(new GrowthIntelligenceContent.TestCase(
                        c.path("name").asText("unnamed"),
                        pts == null ? List.of() : pts,
                        expect,
                        c.path("expectReferral").asBoolean(false),
                        c.path("expectBlocked").asBoolean(false),
                        c.path("expectInsufficient").asBoolean(false)));
            }

            log.info("Loaded growth intelligence pack {} version={} approval={} signals={} rejected={}",
                    root.path("packId").asText(CONTENT_PATH), root.path("version").asText("unknown"),
                    root.path("approvalStatus").asText("ENGINEERING_SEED"), signals.size(), rejected);

            return new GrowthIntelligenceContent(
                    root.path("packId").asText(CONTENT_PATH),
                    root.path("version").asText("unknown"),
                    root.path("approvalStatus").asText("ENGINEERING_SEED"),
                    root.path("adaptationAuthority").asText(null),
                    root.path("provenance").asText(null),
                    List.copyOf(signals),
                    List.copyOf(cases));

        } catch (IOException e) {
            log.error("Failed to read growth intelligence content: {}", e.getMessage(), e);
            return empty();
        }
    }

    private static GrowthIntelligenceContent empty() {
        return new GrowthIntelligenceContent(CONTENT_PATH, "unavailable", "ENGINEERING_SEED",
                null, null, List.of(), List.of());
    }

    private static BigDecimal dec(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).decimalValue() : null;
    }

    private static BigDecimal decimal(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            Object v = row.get(k);
            if (v instanceof Number num) {
                return new BigDecimal(num.toString());
            }
            if (v != null && !String.valueOf(v).isBlank()) {
                try {
                    return new BigDecimal(String.valueOf(v).trim());
                } catch (NumberFormatException ignored) {
                    // try the next alias
                }
            }
        }
        return null;
    }

    private static Integer integer(Map<String, Object> row, String... keys) {
        BigDecimal d = decimal(row, keys);
        return d == null ? null : d.intValue();
    }

    private static String text(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            Object v = row.get(k);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v);
            }
        }
        return null;
    }

    /** Serialisation shared by the endpoint and the fixture gate. */
    public static Map<String, Object> toMap(GrowthIntelligenceEngine.Assessment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assessable", a.assessable());
        m.put("not_assessable_reason", a.notAssessableReason());
        m.put("interpretation_blocked", a.interpretationBlocked());
        // Distinguishes "compared and found nothing" from "not compared". A surface that draws a
        // trend line or a reassuring tick must not do either on the strength of an empty signal
        // list alone.
        m.put("z_trend_derivable", a.zTrendDerivable());
        m.put("any_concern", a.anyConcern());
        m.put("referral_required", a.referralRequired());
        m.put("contacts_considered", a.contactsConsidered());
        List<Map<String, Object>> signals = new ArrayList<>();
        for (GrowthIntelligenceEngine.Signal s : a.signals()) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("code", s.code());
            sm.put("name", s.name());
            sm.put("severity", s.severity());
            sm.put("measure", s.measure());
            sm.put("z_change", s.zChange());
            sm.put("from_age_days", s.fromAgeDays());
            sm.put("to_age_days", s.toAgeDays());
            sm.put("action", s.action());
            sm.put("referral_required", s.referralRequired());
            sm.put("rationale", s.rationale());
            signals.add(sm);
        }
        m.put("signals", signals);
        m.put("content_version", a.contentVersion());
        m.put("content_source", a.contentSource());
        m.put("approval_status", a.approvalStatus());
        m.put("note", a.note());
        return m;
    }
}
