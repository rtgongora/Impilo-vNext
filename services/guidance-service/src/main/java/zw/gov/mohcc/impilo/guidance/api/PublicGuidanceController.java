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
 *   <li>GET /v1/public/guidance/education                — published education topics (safe fields)</li>
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

    public PublicGuidanceController(GatewayEscalationExplainerRepository explainerRepository,
                                    GuidanceService guidanceService) {
        this.explainerRepository = explainerRepository;
        this.guidanceService = guidanceService;
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        Page<KnowledgeArticleEntity> articles =
                guidanceService.getEducation(PUBLIC_TENANT, domain, safePage, safeSize);
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
}
