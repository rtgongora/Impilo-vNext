package zw.gov.mohcc.impilo.guidance.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.guidance.persistence.entity.AdvisoryImpressionEntity;
import zw.gov.mohcc.impilo.guidance.persistence.entity.ServiceAdvisoryEntity;
import zw.gov.mohcc.impilo.guidance.persistence.repository.AdvisoryImpressionRepository;
import zw.gov.mohcc.impilo.guidance.persistence.repository.ServiceAdvisoryRepository;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Nompilo Service Advisory read path — resolves the active, in-window, targeting-matched
 * advisory set for a caller context and records impressions/dismissals.
 *
 * <p>Advisories are DATA (never hard-coded in the frontend). Only status=PUBLISHED
 * advisories whose display window contains {@code now} are considered; the remaining
 * targeting selectors (audience, device, language, geography, service/app, journey stage)
 * are matched against the context. A selector that is absent from an advisory's targeting
 * matches everything; a present selector must equal the caller's context value. The public
 * lane always constrains {@code audience} to {@code public}.</p>
 */
@Service
public class AdvisoryResolveService {

    private static final Logger log = LoggerFactory.getLogger(AdvisoryResolveService.class);

    /** Severity ordering (highest first) for ranking the resolved set. */
    private static final List<String> SEVERITY_RANK =
            List.of("EMERGENCY_ALERT", "IMPORTANT_DISRUPTION", "SERVICE_NOTICE", "INFORMATIONAL");

    private final ServiceAdvisoryRepository advisoryRepo;
    private final AdvisoryImpressionRepository impressionRepo;
    private final ObjectMapper objectMapper;

    public AdvisoryResolveService(ServiceAdvisoryRepository advisoryRepo,
                                  AdvisoryImpressionRepository impressionRepo,
                                  ObjectMapper objectMapper) {
        this.advisoryRepo = advisoryRepo;
        this.impressionRepo = impressionRepo;
        this.objectMapper = objectMapper;
    }

    /** The caller context the resolve lane matches advisories against. */
    public record AdvisoryContext(
            String tenantId,
            String audience,       // public | authenticated
            String deviceType,     // optional
            String language,       // optional
            String province,       // optional
            String district,       // optional
            String serviceKey,     // optional
            String appKey,         // optional
            String journeyStage,   // optional
            OffsetDateTime now
    ) {
        public AdvisoryContext {
            tenantId = (tenantId == null || tenantId.isBlank()) ? "national-spine" : tenantId;
            audience = audience == null ? null : audience.toLowerCase(Locale.ROOT);
            now = now == null ? OffsetDateTime.now() : now;
        }
    }

    /**
     * Resolve the active, in-window, matching advisories ordered by severity (highest first),
     * then most recently updated.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> resolve(AdvisoryContext ctx) {
        return advisoryRepo.findActive(ctx.tenantId(), ctx.now()).stream()
                .filter(a -> matches(a, ctx))
                .sorted(Comparator
                        .comparingInt((ServiceAdvisoryEntity a) -> severityRank(a.getSeverity()))
                        .thenComparing(a -> a.getUpdatedAt() == null ? OffsetDateTime.MIN : a.getUpdatedAt(),
                                Comparator.reverseOrder()))
                .map(this::toView)
                .collect(Collectors.toList());
    }

    /** Valid public notice categories (V017). */
    private static final Set<String> NOTICE_CATEGORIES = Set.of(
            "SERVICE_STATUS", "PUBLIC_HEALTH_CAMPAIGN", "SAFETY_RECALL",
            "REGULATORY_NOTICE", "DISASTER_ALERT", "PROCUREMENT_NOTICE");

    /**
     * Public Notices &amp; Bulletins archive (V017): PUBLISHED, in-window, public-audience
     * notices, optionally filtered by category and province, newest first, paginated. Reuses
     * the advisory targeting match so a notice may still be geo-scoped. Allow-listed view
     * (adds category + a citizen-facing "publishedAt"); no authoring internals, no PII.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> listNotices(String category, String province, int page, int size) {
        String cat = (category == null || category.isBlank()) ? null
                : category.trim().toUpperCase(Locale.ROOT);
        if (cat != null && !NOTICE_CATEGORIES.contains(cat)) {
            cat = null; // unknown category → unfiltered, never an error oracle
        }
        OffsetDateTime now = OffsetDateTime.now();
        AdvisoryContext ctx = new AdvisoryContext(
                "national-spine", "public", null, null, province, null, null, null, null, now);
        List<ServiceAdvisoryEntity> all = advisoryRepo.findActiveNotices("national-spine", cat, now)
                .stream()
                .filter(a -> matches(a, ctx))
                .toList();
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        List<Map<String, Object>> items = all.subList(from, to).stream()
                .map(this::toNoticeView)
                .collect(Collectors.toList());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("notices", items);
        out.put("total", all.size());
        out.put("page", safePage);
        out.put("size", safeSize);
        return out;
    }

    /** Allow-listed public notice view — the advisory fields plus category and publishedAt. */
    private Map<String, Object> toNoticeView(ServiceAdvisoryEntity a) {
        Map<String, Object> m = toView(a);
        m.put("category", a.getCategory());
        m.put("publishedAt", a.getStartsAt() == null
                ? (a.getUpdatedAt() == null ? null : a.getUpdatedAt().toString())
                : a.getStartsAt().toString());
        return m;
    }

    // ── targeting match ──────────────────────────────────────────────────────────────

    private boolean matches(ServiceAdvisoryEntity a, AdvisoryContext ctx) {
        JsonNode t = parse(a.getTargeting());
        if (t == null || t.isNull() || !t.isObject()) {
            // No targeting = untargeted advisory. The public lane still requires public audience,
            // so an untargeted advisory is only shown to public callers when public was requested.
            return ctx.audience() == null || "public".equals(ctx.audience());
        }
        if (!selectorMatches(t, "audience", ctx.audience())) return false;
        if (!selectorMatches(t, "deviceType", ctx.deviceType())) return false;
        if (!selectorMatches(t, "language", ctx.language())) return false;
        if (!selectorMatches(t, "province", ctx.province())) return false;
        if (!selectorMatches(t, "district", ctx.district())) return false;
        if (!selectorMatches(t, "serviceKey", ctx.serviceKey())) return false;
        if (!selectorMatches(t, "appKey", ctx.appKey())) return false;
        if (!selectorMatches(t, "journeyStage", ctx.journeyStage())) return false;
        return true;
    }

    /**
     * A selector matches when the advisory does not constrain it (field absent/blank), or when the
     * caller's context value equals the advisory's value (case-insensitive). If the advisory
     * constrains a selector the caller left unset, it does not match.
     */
    private boolean selectorMatches(JsonNode targeting, String field, String contextValue) {
        JsonNode node = targeting.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return true; // advisory does not constrain this dimension
        }
        if (contextValue == null || contextValue.isBlank()) {
            return false; // advisory constrains it but caller has no value
        }
        return node.asText().equalsIgnoreCase(contextValue);
    }

    private int severityRank(String severity) {
        int idx = SEVERITY_RANK.indexOf(severity == null ? "" : severity.toUpperCase(Locale.ROOT));
        return idx < 0 ? SEVERITY_RANK.size() : idx;
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Advisory JSON parse failed: {}", e.getMessage());
            return null;
        }
    }

    /** Allow-listed public disclosure of a resolved advisory (no PII, no authoring internals). */
    private Map<String, Object> toView(ServiceAdvisoryEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("title", a.getTitle());
        m.put("body", a.getBody());
        m.put("severity", a.getSeverity());
        m.put("variant", a.getVariant());
        m.put("primaryActions", parseActions(a.getPrimaryActions()));
        m.put("startsAt", a.getStartsAt() == null ? null : a.getStartsAt().toString());
        m.put("expiresAt", a.getExpiresAt() == null ? null : a.getExpiresAt().toString());
        return m;
    }

    private List<Map<String, Object>> parseActions(String json) {
        JsonNode node = parse(json);
        if (node == null || !node.isArray()) return List.of();
        List<Map<String, Object>> actions = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isObject()) {
                Map<String, Object> action = new LinkedHashMap<>();
                action.put("label", item.path("label").asText(null));
                action.put("href", item.path("href").asText(null));
                actions.add(action);
            }
        }
        return actions;
    }

    // ── impressions / dismissals ───────────────────────────────────────────────────────

    /** Record an interaction impression (VIEW | DISMISS | FEEDBACK). */
    @Transactional
    public void recordImpression(String tenantId, UUID advisoryId, String event, String anonDismissalKey) {
        String normalized = event == null ? "VIEW" : event.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("VIEW", "DISMISS", "FEEDBACK").contains(normalized)) {
            normalized = "VIEW";
        }
        AdvisoryImpressionEntity imp = new AdvisoryImpressionEntity();
        imp.setTenantId(tenantId == null || tenantId.isBlank() ? "national-spine" : tenantId);
        imp.setAdvisoryId(advisoryId);
        imp.setEvent(normalized);
        imp.setAnonDismissalKey(anonDismissalKey);
        impressionRepo.save(imp);
        log.debug("Advisory impression recorded: advisory={} event={}", advisoryId, normalized);
    }
}
