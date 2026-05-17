package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nompilo composer assistant for the social timeline.
 *
 * Operations supported via {@code POST /internal/v1/social/composer/assist}
 * with an {@code op} field:
 *   improve, simplify, translate, suggest_tags, suggest_audience,
 *   safety_check, draft_announcement, summarise_thread.
 *
 * The controller proxies to {@code llm-orchestration-service} (default
 * provider Gemini, with adapter fallback to OpenAI / Anthropic / DeepSeek /
 * deterministic stub). If the LLM stack is unreachable, it returns a
 * deterministic, safe baseline response so the UI never blocks on the AI.
 */
@RestController
@RequestMapping("/internal/v1/social/composer")
public class SocialComposerController {

    private static final Logger log = LoggerFactory.getLogger(SocialComposerController.class);

    private final RestTemplate restTemplate;
    private final String llmBaseUrl;

    public SocialComposerController(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.llm-orchestration-base-url:http://localhost:8265}") String llmBaseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.llmBaseUrl = llmBaseUrl;
    }

    @PostMapping("/assist")
    public ResponseEntity<Map<String, Object>> assist(@RequestBody Map<String, Object> body) {
        String op = String.valueOf(body.getOrDefault("op", "improve")).toLowerCase();
        String content = String.valueOf(body.getOrDefault("content", ""));
        String language = String.valueOf(body.getOrDefault("language", "en"));
        String audience = String.valueOf(body.getOrDefault("audience", "general"));

        Map<String, Object> deterministic = deterministicFallback(op, content, language, audience);
        try {
            String system = systemPromptFor(op, language, audience);
            String user = userPromptFor(op, content, language, audience);
            Map<String, Object> payload = Map.of(
                    "useCase", "NOMPILO_SOCIAL_COMPOSER_" + op.toUpperCase(),
                    "actorContext", Map.of("purposeOfUse", "SOCIAL_COMPOSER"),
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)
                    ),
                    "requiredCapabilities", List.of("CHAT"),
                    "riskLevel", riskFor(op),
                    "requiresAudit", isAuditedOp(op),
                    "requiresHumanApprovalForActions", false,
                    "maxOutputTokens", 600,
                    "temperature", op.equals("safety_check") ? 0.0 : 0.4);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    llmBaseUrl + "/internal/v1/llm/chat", payload, Map.class);
            Map<String, Object> raw = response.getBody();
            if (raw == null || raw.get("content") == null) {
                return ResponseEntity.ok(deterministic);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("op", op);
            out.put("language", language);
            out.put("audience", audience);
            out.put("provider", raw.get("provider"));
            out.put("model", raw.get("model"));
            out.put("fallbackUsed", raw.get("fallbackUsed"));
            out.put("result", raw.get("content"));
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            log.warn("LLM assist failed op={} err={} — returning deterministic fallback", op, e.getMessage());
            deterministic.put("note", "LLM unavailable, returned deterministic fallback");
            return ResponseEntity.ok(deterministic);
        }
    }

    private static String systemPromptFor(String op, String language, String audience) {
        return switch (op) {
            case "improve" -> "You are Nompilo, a friendly health communication assistant. Rewrite the user's post to be clearer, warmer, and free of clinical jargon while keeping it accurate. Return only the rewritten post.";
            case "simplify" -> "You are Nompilo. Simplify the post for a general audience with mixed literacy. Use short sentences and concrete actions. Return only the simplified post.";
            case "translate" -> "You are Nompilo. Translate the user's post into " + language + ". Keep tone warm, accurate, and respectful. Return only the translation.";
            case "suggest_tags" -> "You are Nompilo. Suggest 3-7 relevant short hashtags or topics for the post. Return a single line of space-separated #tags.";
            case "suggest_audience" -> "You are Nompilo. Suggest which audience scope this post belongs to (public, community, group, page_followers, role_only, private) with a one-sentence rationale. Return JSON: {\"audience\": ..., \"why\": ...}.";
            case "safety_check" -> "You are Nompilo. Check the post for unsafe content: clinical advice without verification, identifiable patient data, hate, harassment, misinformation. Return JSON: {\"safe\": true|false, \"concerns\": [..], \"suggestion\": \"...\"}.";
            case "draft_announcement" -> "You are Nompilo, drafting an official health announcement for the " + audience + " audience in " + language + ". Keep it short, warm, and actionable.";
            case "summarise_thread" -> "You are Nompilo. Summarise this thread in 2-3 sentences for a busy reader.";
            default -> "You are Nompilo, a friendly health communication assistant.";
        };
    }

    private static String userPromptFor(String op, String content, String language, String audience) {
        return switch (op) {
            case "suggest_tags" -> "POST:\n\n" + content;
            case "safety_check", "suggest_audience" -> "POST:\n\n" + content;
            default -> content;
        };
    }

    private static String riskFor(String op) {
        return switch (op) {
            case "safety_check", "draft_announcement" -> "MODERATE";
            default -> "LOW";
        };
    }

    private static boolean isAuditedOp(String op) {
        return switch (op) {
            case "draft_announcement", "safety_check" -> true;
            default -> false;
        };
    }

    /** Deterministic baseline that always works even when the LLM is offline. */
    private static Map<String, Object> deterministicFallback(String op, String content, String language, String audience) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("op", op);
        out.put("language", language);
        out.put("audience", audience);
        out.put("provider", "deterministic-fallback");
        out.put("fallbackUsed", true);
        switch (op) {
            case "improve" -> out.put("result", improveDeterministic(content));
            case "simplify" -> out.put("result", simplifyDeterministic(content));
            case "translate" -> out.put("result", content + (language.equals("en") ? "" : " [auto-translation pending: " + language + "]"));
            case "suggest_tags" -> out.put("result", deterministicTags(content));
            case "suggest_audience" -> out.put("result", Map.of("audience", suggestAudience(content), "why", "Default scope for content type."));
            case "safety_check" -> out.put("result", deterministicSafety(content));
            case "draft_announcement" -> out.put("result", "Health update: " + content);
            case "summarise_thread" -> out.put("result", content.length() > 280 ? content.substring(0, 280) + "…" : content);
            default -> out.put("result", content);
        }
        return out;
    }

    private static String improveDeterministic(String content) {
        if (content == null || content.isBlank()) return "Share a quick update with your community.";
        String trimmed = content.trim();
        if (trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")) return trimmed;
        return trimmed + ".";
    }

    private static String simplifyDeterministic(String content) {
        if (content == null) return "";
        return content.replaceAll("\\b(approximately|approximately)\\b", "about")
                .replaceAll("\\b(utilise|utilize)\\b", "use")
                .replaceAll("\\b(commence)\\b", "start")
                .replaceAll("\\b(terminate)\\b", "stop")
                .replaceAll("\\b(facilitate)\\b", "help")
                .trim();
    }

    private static String deterministicTags(String content) {
        if (content == null || content.isBlank()) return "#health #community";
        String lower = content.toLowerCase();
        List<String> tags = new ArrayList<>();
        Map<String, String> mapping = Map.of(
                "malaria", "#malaria",
                "diabetes", "#diabetes",
                "pregnan", "#maternal-health",
                "vaccine", "#immunisation",
                "child", "#child-health",
                "walk", "#wellness",
                "mental", "#mental-wellness"
        );
        mapping.forEach((k, v) -> { if (lower.contains(k)) tags.add(v); });
        if (tags.isEmpty()) tags.add("#health");
        if (!tags.contains("#community")) tags.add("#community");
        return String.join(" ", tags);
    }

    private static String suggestAudience(String content) {
        if (content == null) return "public";
        String l = content.toLowerCase();
        if (l.contains("staff only") || l.contains("internal")) return "role_only";
        if (l.contains("our facility") || l.contains("clinic team")) return "facility";
        return "public";
    }

    private static Map<String, Object> deterministicSafety(String content) {
        if (content == null) return Map.of("safe", true, "concerns", List.of(), "suggestion", "Looks fine.");
        String l = content.toLowerCase();
        List<String> concerns = new ArrayList<>();
        if (l.matches(".*\\b\\d{6,}\\b.*")) concerns.add("Possible identifier (long number) detected.");
        if (l.contains("diagnose") || l.contains("prescription")) concerns.add("Clinical advice should be verified or labelled non-clinical.");
        boolean safe = concerns.isEmpty();
        return Map.of("safe", safe, "concerns", concerns,
                "suggestion", safe ? "Looks fine." : "Consider rewording the flagged parts or selecting a more restricted audience.");
    }
}
