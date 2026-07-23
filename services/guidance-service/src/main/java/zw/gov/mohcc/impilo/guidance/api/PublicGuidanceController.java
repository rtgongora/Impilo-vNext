package zw.gov.mohcc.impilo.guidance.api;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.guidance.core.GuidanceService;
import zw.gov.mohcc.impilo.guidance.persistence.entity.GatewayEscalationExplainerEntity;
import zw.gov.mohcc.impilo.guidance.persistence.entity.KnowledgeArticleEntity;
import zw.gov.mohcc.impilo.guidance.persistence.repository.GatewayEscalationExplainerRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Public (R0, anonymous) guidance lane — gateway doctrine §9 / public-lane ADR.
 *
 * <p>Follows the {@code Public*Controller} rule: allow-listed DTOs only, NO
 * personalization, NO PII, and no internal service names in payloads. Consumed by the
 * experience-bff {@code PublicGatewayGuidanceBffController} under
 * {@code /internal/v1/public/gateway/guidance/**}; never exposed to the UI directly.</p>
 *
 * <ul>
 *   <li>GET /v1/public/guidance/explain-steps            — all active escalation explainers</li>
 *   <li>GET /v1/public/guidance/explain-steps/{stepKey}  — one explainer (why / level / next / help)</li>
 *   <li>GET /v1/public/guidance/education                — published education topics (safe fields; ?domain= / ?category= / ?q= text search)</li>
 *   <li>GET /v1/public/guidance/education/categories     — published-topic categories with counts</li>
 *   <li>GET /v1/public/guidance/education/{id}           — one published article incl. plain-language body</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/public/guidance")
public class PublicGuidanceController {

    /** Public lane is pre-tenant: content is national-spine citizen education. */
    static final String PUBLIC_TENANT = "national-spine";
    static final String ACTIVE = "ACTIVE";

    private final GatewayEscalationExplainerRepository explainerRepository;
    private final GuidanceService guidanceService;

    private final zw.gov.mohcc.impilo.guidance.core.AdvisoryResolveService advisoryResolveService;

    public PublicGuidanceController(GatewayEscalationExplainerRepository explainerRepository,
                                    GuidanceService guidanceService,
                                    zw.gov.mohcc.impilo.guidance.core.AdvisoryResolveService advisoryResolveService) {
        this.explainerRepository = explainerRepository;
        this.guidanceService = guidanceService;
        this.advisoryResolveService = advisoryResolveService;
    }

    /**
     * Public Notices &amp; Bulletins archive (V017) — a browsable list of PUBLISHED,
     * in-window, public-audience notices (campaigns, safety recalls, disaster alerts,
     * regulatory notices, procurement notices, service status), optionally filtered by
     * category and province. Allow-listed fields only (no authoring internals, no PII);
     * an unknown category is treated as unfiltered rather than an error.
     */
    @GetMapping("/notices")
    public ResponseEntity<Map<String, Object>> notices(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String province,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ResponseEntity.ok(Map.of("data",
                advisoryResolveService.listNotices(category, province, page, size)));
    }

    @GetMapping("/explain-steps")
    public ResponseEntity<Map<String, Object>> listExplainSteps() {
        List<Map<String, Object>> data = explainerRepository
                .findByTenantIdAndStatusOrderByStepKeyAsc(PUBLIC_TENANT, ACTIVE)
                .stream()
                .map(PublicGuidanceController::toPublicExplainStep)
                .toList();
        return ResponseEntity.ok(Map.of("data", data));
    }

    @GetMapping("/explain-steps/{stepKey}")
    public ResponseEntity<Map<String, Object>> explainStep(@PathVariable String stepKey) {
        String normalized = stepKey == null ? "" : stepKey.trim().toLowerCase(Locale.ROOT);
        return explainerRepository
                .findByTenantIdAndStepKeyAndStatus(PUBLIC_TENANT, normalized, ACTIVE)
                .map(e -> ResponseEntity.ok(Map.of("data", (Object) toPublicExplainStep(e))))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "error", Map.of(
                                "code", "EXPLAIN_STEP_NOT_FOUND",
                                "message", "No guidance exists for this step."))));
    }

    @GetMapping("/education")
    public ResponseEntity<Map<String, Object>> education(
            @RequestParam(defaultValue = "all") String domain,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(required = false, name = "q") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        String safeCategory = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        String safeQuery = q == null ? "" : q.trim();
        // A text query takes precedence over the domain/category browse: it runs the PUBLISHED-only
        // title/summary search (guidanceService.search already scopes status='PUBLISHED').
        Page<KnowledgeArticleEntity> articles;
        if (!safeQuery.isEmpty()) {
            articles = guidanceService.search(PUBLIC_TENANT, safeQuery, safePage, safeSize);
        } else if (safeCategory.isEmpty()) {
            articles = guidanceService.getEducation(PUBLIC_TENANT, domain, safePage, safeSize);
        } else {
            articles = guidanceService.getEducationByCategory(PUBLIC_TENANT, safeCategory, safePage, safeSize);
        }
        List<Map<String, Object>> data = articles.getContent().stream()
                .map(PublicGuidanceController::toPublicEducationTopic)
                .toList();
        return ResponseEntity.ok(Map.of(
                "data", data,
                "meta", Map.of(
                        "page", safePage,
                        "size", safeSize,
                        "totalElements", articles.getTotalElements(),
                        "totalPages", articles.getTotalPages())));
    }

    /** Published-topic categories with counts — the public health-info topic index. */
    @GetMapping("/education/categories")
    public ResponseEntity<Map<String, Object>> educationCategories() {
        List<Map<String, Object>> data = guidanceService.getEducationCategories(PUBLIC_TENANT).stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("category", c.getCategory());
                    m.put("count", c.getCount());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(Map.of("data", data));
    }

    /** One published article, including its plain-language body (allow-listed fields only). */
    @GetMapping("/education/{id}")
    public ResponseEntity<Map<String, Object>> educationArticle(@PathVariable String id) {
        java.util.UUID articleId;
        try {
            articleId = java.util.UUID.fromString(id == null ? "" : id.trim());
        } catch (IllegalArgumentException e) {
            return articleNotFound();
        }
        return guidanceService.getPublishedArticle(PUBLIC_TENANT, articleId)
                .map(a -> ResponseEntity.ok(Map.of("data", (Object) toPublicEducationArticle(a))))
                .orElseGet(PublicGuidanceController::articleNotFound);
    }

    private static ResponseEntity<Map<String, Object>> articleNotFound() {
        return ResponseEntity.status(404).body(Map.of(
                "error", Map.of(
                        "code", "EDUCATION_ARTICLE_NOT_FOUND",
                        "message", "This article does not exist or is not published.")));
    }

    /** Allow-listed explainer disclosure — the four doctrinal parts plus routing labels. */
    private static Map<String, Object> toPublicExplainStep(GatewayEscalationExplainerEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stepKey", e.getStepKey());
        m.put("pillar", e.getPillar());
        m.put("rungFrom", e.getRungFrom());
        m.put("rungTo", e.getRungTo());
        m.put("title", e.getTitle());
        m.put("why", e.getWhy());
        m.put("levelLabel", e.getLevelLabel());
        m.put("whatNext", e.getWhatNext());
        m.put("howToGetHelp", e.getHowToGetHelp());
        m.put("continueWithoutLabel", e.getContinueWithoutLabel());
        return m;
    }

    /** Allow-listed education disclosure: id/title/summary/category/domain only. */
    private static Map<String, Object> toPublicEducationTopic(KnowledgeArticleEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("title", a.getTitle());
        m.put("summary", a.getSummary());
        m.put("category", a.getCategory());
        m.put("domain", a.getDomain());
        return m;
    }

    // ── Anonymous grounded Q&A (public Nompilo ask lane) ──────────────────────────

    /** Question bounds: enough for a real question, never an unbounded anonymous payload. */
    static final int MAX_QUESTION_CHARS = 500;

    static final String PUBLIC_ASK_DISCLAIMER =
            "Nompilo gives general health guidance, not a diagnosis or personal medical advice. "
                    + "If this is an emergency, call the emergency numbers or go to the nearest "
                    + "health facility now.";

    /**
     * POST /v1/public/guidance/ask — anonymous, grounded, honesty-gated Q&A.
     *
     * <p>Public*Controller rules hold: personalization is FORCED OFF, the actor is the fixed
     * anonymous principal (no caller identity is read or stored), and the response is an
     * allow-listed subset of {@link GuidanceService#ask}: the grounded answer text, the
     * answer source ({@code llm} / {@code retrieval} / {@code none} — the honesty gate),
     * confidence, source titles (never URLs), and a standing not-a-diagnosis disclaimer.
     * Rate limiting lives on the BFF gateway lane in front of this.</p>
     */
    @org.springframework.web.bind.annotation.PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("question");
        String question = raw == null ? "" : String.valueOf(raw).trim();
        if (question.length() < 3) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "QUESTION_REQUIRED", "message", "Ask a short question.")));
        }
        if (question.length() > MAX_QUESTION_CHARS) {
            question = question.substring(0, MAX_QUESTION_CHARS);
        }

        Map<String, Object> result =
                guidanceService.ask(PUBLIC_TENANT, "public-anonymous", question, false);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("answer", result.get("response"));
        out.put("answerSource", result.get("answerSource"));
        out.put("confidence", result.get("confidence"));
        Object sources = result.get("sources");
        if (sources instanceof List<?> list) {
            out.put("sources", list.stream()
                    .filter(s -> s instanceof Map<?, ?>)
                    .map(s -> ((Map<?, ?>) s).get("title"))
                    .filter(t -> t != null && !String.valueOf(t).isBlank())
                    .map(String::valueOf)
                    .toList());
        }
        out.put("disclaimer", PUBLIC_ASK_DISCLAIMER);
        return ResponseEntity.ok(out);
    }

    /**
     * Allow-listed article read: the topic fields plus the plain-language body and the
     * last-updated date (freshness cue). Source/sourceUrl/tenant/relevance never leak.
     */
    private static Map<String, Object> toPublicEducationArticle(KnowledgeArticleEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("title", a.getTitle());
        m.put("summary", a.getSummary());
        m.put("body", a.getContent());
        m.put("category", a.getCategory());
        m.put("domain", a.getDomain());
        m.put("updatedAt", a.getUpdatedAt() == null ? null : a.getUpdatedAt().toString());
        return m;
    }
}
