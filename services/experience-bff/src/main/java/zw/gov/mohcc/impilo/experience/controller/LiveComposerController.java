package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nompilo composer assistant for Impilo Live event operations.
 *
 * Operations via {@code POST /internal/v1/live/composer/assist} with {@code op}:
 * explain_event, generate_agenda, generate_description, summarise_replay,
 * recommend_followup, safety_check.
 */
@RestController
@RequestMapping("/internal/v1/live/composer")
public class LiveComposerController {

    private static final Logger log = LoggerFactory.getLogger(LiveComposerController.class);

    private final RestTemplate restTemplate;
    private final String llmBaseUrl;

    public LiveComposerController(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.llm-orchestration-base-url:http://localhost:8265}") String llmBaseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.llmBaseUrl = llmBaseUrl;
    }

    @PostMapping("/assist")
    public ResponseEntity<Map<String, Object>> assist(@RequestBody Map<String, Object> body) {
        String op = String.valueOf(body.getOrDefault("op", "explain_event")).toLowerCase();
        String language = String.valueOf(body.getOrDefault("language", "en"));
        String audience = String.valueOf(body.getOrDefault("audience", "general"));
        @SuppressWarnings("unchecked")
        Map<String, Object> context = body.get("context") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        String content = String.valueOf(body.getOrDefault("content",
                context.getOrDefault("title", context.getOrDefault("description", ""))));

        Map<String, Object> deterministic = deterministicFallback(op, content, language, audience, context);
        try {
            String system = systemPromptFor(op, language, audience);
            String user = userPromptFor(op, content, language, audience, context);
            Map<String, Object> payload = Map.of(
                    "useCase", "NOMPILO_LIVE_COMPOSER_" + op.toUpperCase(),
                    "actorContext", Map.of("purposeOfUse", "LIVE_COMPOSER"),
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)
                    ),
                    "requiredCapabilities", List.of("CHAT"),
                    "riskLevel", riskFor(op),
                    "requiresAudit", isAuditedOp(op),
                    "requiresHumanApprovalForActions", false,
                    "maxOutputTokens", 800,
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
            log.warn("LLM live assist failed op={} err={} — returning deterministic fallback", op, e.getMessage());
            deterministic.put("note", "LLM unavailable, returned deterministic fallback");
            return ResponseEntity.ok(deterministic);
        }
    }

    private static String systemPromptFor(String op, String language, String audience) {
        return switch (op) {
            case "explain_event" ->
                    "You are Nompilo, a friendly health education assistant. Explain the live event clearly for "
                            + audience + " in " + language + ". Cover purpose, who should attend, and what to expect. "
                            + "Do not give personalised clinical advice.";
            case "generate_agenda" ->
                    "You are Nompilo. Draft a concise live event agenda with timed segments, speaker notes, and "
                            + "audience interaction points. Return plain text or markdown.";
            case "generate_description" ->
                    "You are Nompilo. Write a warm, accurate event description for " + audience
                            + " in " + language + ". Highlight learning outcomes without clinical claims.";
            case "summarise_replay" ->
                    "You are Nompilo. Summarise the replay transcript or notes in 3-5 bullet points for busy "
                            + audience + " readers.";
            case "recommend_followup" ->
                    "You are Nompilo. Suggest appropriate follow-up resources (courses, articles, community groups) "
                            + "based on the event topic. Return JSON: {\"recommendations\": [{\"title\": ..., "
                            + "\"type\": ..., \"reason\": ...}]}.";
            case "safety_check" ->
                    "You are Nompilo. Review live event copy for unsafe content: unverified clinical advice, "
                            + "identifiable patient data, hate, harassment, misinformation. Return JSON: "
                            + "{\"safe\": true|false, \"concerns\": [..], \"suggestion\": \"...\"}.";
            default -> "You are Nompilo, a friendly health education assistant for live events.";
        };
    }

    private static String userPromptFor(String op, String content, String language, String audience,
                                        Map<String, Object> context) {
        String ctx = context.isEmpty() ? content : context.toString();
        return switch (op) {
            case "generate_agenda", "generate_description" -> "EVENT CONTEXT:\n\n" + ctx;
            case "recommend_followup", "safety_check" -> "EVENT CONTENT:\n\n" + content;
            default -> content;
        };
    }

    private static String riskFor(String op) {
        return switch (op) {
            case "safety_check", "recommend_followup" -> "MODERATE";
            default -> "LOW";
        };
    }

    private static boolean isAuditedOp(String op) {
        return switch (op) {
            case "safety_check", "recommend_followup" -> true;
            default -> false;
        };
    }

    private static Map<String, Object> deterministicFallback(String op, String content, String language,
                                                             String audience, Map<String, Object> context) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("op", op);
        out.put("language", language);
        out.put("audience", audience);
        out.put("provider", "deterministic-fallback");
        out.put("fallbackUsed", true);
        String title = String.valueOf(context.getOrDefault("title", "Live event"));
        switch (op) {
            case "explain_event" -> out.put("result",
                    "Join \"" + title + "\" to learn more in a guided live session. "
                            + "Bring questions and stay for the Q&A if available.");
            case "generate_agenda" -> out.put("result",
                    "1. Welcome & housekeeping (5 min)\n2. Main presentation (25 min)\n"
                            + "3. Q&A (15 min)\n4. Closing & next steps (5 min)");
            case "generate_description" -> out.put("result",
                    content != null && !content.isBlank()
                            ? content.trim()
                            : "A live health education session for " + audience + ".");
            case "summarise_replay" -> out.put("result",
                    content != null && content.length() > 320
                            ? content.substring(0, 320) + "…"
                            : (content != null ? content : "Replay summary pending."));
            case "recommend_followup" -> out.put("result", Map.of(
                    "recommendations", List.of(
                            Map.of("title", "Related learning module", "type", "course",
                                    "reason", "Deepen understanding after the live session."),
                            Map.of("title", "Community discussion", "type", "community",
                                    "reason", "Continue the conversation with peers."))));
            case "safety_check" -> out.put("result", deterministicSafety(content));
            default -> out.put("result", content != null ? content : "");
        }
        return out;
    }

    private static Map<String, Object> deterministicSafety(String content) {
        if (content == null || content.isBlank()) {
            return Map.of("safe", true, "concerns", List.of(), "suggestion", "Looks fine.");
        }
        String lower = content.toLowerCase();
        List<String> concerns = new ArrayList<>();
        if (lower.matches(".*\\b\\d{6,}\\b.*")) {
            concerns.add("Possible identifier (long number) detected.");
        }
        if (lower.contains("diagnose") || lower.contains("prescription")) {
            concerns.add("Clinical advice should be verified or labelled non-clinical.");
        }
        boolean safe = concerns.isEmpty();
        return Map.of(
                "safe", safe,
                "concerns", concerns,
                "suggestion", safe ? "Looks fine." : "Review flagged content before going live.");
    }
}
