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
 * Nompilo donor journey assistant for MADI.
 *
 * Operations via {@code POST /internal/v1/madi/donor/assist} with {@code op}:
 * explain_donation_steps, explain_eligibility, prescreening_guide,
 * explain_deferral_safely, find_drives_help, interpret_prescreening_outcome.
 */
@RestController
@RequestMapping("/internal/v1/madi/donor")
public class MadiDonorAssistController {

    private static final Logger log = LoggerFactory.getLogger(MadiDonorAssistController.class);

    private final RestTemplate restTemplate;
    private final String llmBaseUrl;

    public MadiDonorAssistController(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.llm-orchestration-base-url:http://localhost:8265}") String llmBaseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.llmBaseUrl = llmBaseUrl;
    }

    @PostMapping("/assist")
    public ResponseEntity<Map<String, Object>> assist(@RequestBody Map<String, Object> body) {
        String op = String.valueOf(body.getOrDefault("op", "explain_donation_steps")).toLowerCase();
        String language = String.valueOf(body.getOrDefault("language", "en"));
        @SuppressWarnings("unchecked")
        Map<String, Object> context = body.get("context") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();

        Map<String, Object> deterministic = deterministicFallback(op, language, context);
        try {
            String system = systemPromptFor(op, language);
            String user = userPromptFor(op, context);
            Map<String, Object> payload = Map.of(
                    "useCase", "NOMPILO_MADI_DONOR_" + op.toUpperCase(),
                    "actorContext", Map.of("purposeOfUse", "DONOR_ENGAGEMENT"),
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)
                    ),
                    "requiredCapabilities", List.of("CHAT"),
                    "riskLevel", "LOW",
                    "requiresAudit", true,
                    "requiresHumanApprovalForActions", false,
                    "maxOutputTokens", 500,
                    "temperature", 0.3);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    llmBaseUrl + "/internal/v1/llm/chat", payload, Map.class);
            Map<String, Object> raw = response.getBody();
            if (raw == null || raw.get("content") == null) {
                return ResponseEntity.ok(deterministic);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("op", op);
            out.put("language", language);
            out.put("content", raw.get("content"));
            out.put("provider", raw.get("provider"));
            out.put("fallbackUsed", false);
            out.put("disclaimer", NOMPILO_DISCLAIMER);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            log.warn("MADI donor assist LLM unavailable for op={}: {}", op, e.getMessage());
            return ResponseEntity.ok(deterministic);
        }
    }

    private static final String NOMPILO_DISCLAIMER =
            "This is general guidance only — not a medical decision. A nurse will confirm eligibility at the drive.";

    private static String systemPromptFor(String op, String language) {
        return """
                You are Nompilo, Impilo's warm donor companion. Help citizens understand blood donation safely.
                NEVER diagnose, reveal deferral clinical reasons, or give treatment advice.
                Use simple, respectful %s. If someone may be ineligible, use gentle deferral language.
                Operation: %s
                """.formatted(language, op);
    }

    private static String userPromptFor(String op, Map<String, Object> context) {
        return "Operation: " + op + "\nContext: " + context;
    }

    private static Map<String, Object> deterministicFallback(String op, String language, Map<String, Object> ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("op", op);
        out.put("language", language);
        out.put("fallbackUsed", true);
        out.put("disclaimer", NOMPILO_DISCLAIMER);
        String outcome = String.valueOf(ctx.getOrDefault("prescreening_outcome", ""));

        switch (op) {
            case "explain_donation_steps" -> {
                out.put("content", List.of(
                        "Register your interest as a donor in My Life.",
                        "Complete a short wellness check (not a diagnosis).",
                        "Book or walk in to a nearby donation drive.",
                        "A nurse will confirm eligibility in person.",
                        "Rest, hydrate, and receive a thank-you after donating."));
                out.put("steps", out.get("content"));
            }
            case "explain_eligibility" -> out.put("content",
                    "Most healthy adults aged 17–65 can donate. You need valid ID, adequate rest, "
                            + "and no recent illness or high-risk exposure. A nurse makes the final decision — "
                            + "this app cannot tell you if you are medically eligible today.");
            case "prescreening_guide" -> {
                List<Map<String, String>> questions = new ArrayList<>();
                questions.add(Map.of("id", "recent_illness", "label", "Have you felt unwell in the last 7 days?",
                        "help", "Wait until you feel fully recovered before donating."));
                questions.add(Map.of("id", "recent_travel_malaria", "label", "Travelled to a malaria area recently?",
                        "help", "You may need to wait — staff can advise at the drive."));
                questions.add(Map.of("id", "low_hemoglobin_symptoms", "label", "Dizziness, unusual tiredness, or breathlessness?",
                        "help", "Tell staff — they may check your iron level first."));
                questions.add(Map.of("id", "recent_tattoo", "label", "Tattoo or piercing in the last 3 months?",
                        "help", "A short waiting period may apply."));
                questions.add(Map.of("id", "on_antibiotics", "label", "Taking antibiotics now?",
                        "help", "Finish your course and wait before donating."));
                questions.add(Map.of("id", "pregnant_or_recent_birth", "label", "Pregnant or gave birth in the last 6 months?",
                        "help", "You should not donate until cleared by a clinician."));
                out.put("questions", questions);
                out.put("content", "Answer honestly — your answers stay private and help you prepare safely.");
            }
            case "explain_deferral_safely" -> out.put("content",
                    "If you are asked to wait before donating, it is to keep you and patients safe. "
                            + "It does not mean something is seriously wrong. Staff can tell you when to try again "
                            + "without sharing sensitive details here.");
            case "find_drives_help" -> out.put("content",
                    "Open Donation drives near me to see published drives. Bring ID, eat lightly, and drink water.");
            case "interpret_prescreening_outcome" -> {
                if ("PROCEED_TO_DRIVE".equals(outcome)) {
                    out.put("content", "Your answers suggest you may be ready to visit a drive. "
                            + "A nurse will still confirm eligibility in person.");
                } else if ("DEFER_SAFELY".equals(outcome)) {
                    out.put("content", "Based on your answers, it may be better to wait before donating. "
                            + "This is precautionary — contact the blood service if you have questions.");
                } else if ("CONTACT_STAFF".equals(outcome)) {
                    out.put("content", "Please speak with blood bank staff before your next donation attempt. "
                            + "They can guide you safely.");
                } else {
                    out.put("content", "Complete the short wellness check, then we can explain your next step.");
                }
            }
            default -> out.put("content", "I can help you understand donation steps, eligibility, and finding a drive.");
        }
        return out;
    }
}
