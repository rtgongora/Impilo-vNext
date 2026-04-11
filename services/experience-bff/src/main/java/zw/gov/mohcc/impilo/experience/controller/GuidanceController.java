package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Guidance BFF Controller — conversational guidance, reminders, health education,
 * and federated search across the Health OS experience shell.
 *
 * <p>Implements the "Intelligent Experience Shell" doctrine principle: the unified
 * experience is searchable, conversational, context-aware, and provides proactive
 * guidance. Graduated friction applies — wellness/search queries receive MINIMAL
 * friction while clinical guidance routes through higher assurance checks.</p>
 *
 * <p>Federated search spans health, wellness, diet, sleep, service, and marketplace
 * domains, returning a unified result set for the experience shell search bar.</p>
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>POST /internal/v1/guidance/ask — conversational guidance (question in, guidance out)</li>
 *   <li>GET  /internal/v1/guidance/reminders — active reminders/prompts for the user</li>
 *   <li>GET  /internal/v1/guidance/education — health education content for user context</li>
 *   <li>GET  /internal/v1/guidance/search — federated search across all domains</li>
 * </ul>
 *
 * @see <a href="docs/doctrine/health-os-doctrine.md">Health OS Doctrine — Intelligent Experience Shell</a>
 */
@RestController
@RequestMapping("/internal/v1/guidance")
public class GuidanceController {

    private static final Logger log = LoggerFactory.getLogger(GuidanceController.class);

    /**
     * Conversational guidance endpoint.
     *
     * <p>Accepts a question from the user and returns a structured guidance response
     * including answer text, confidence level, source references, and optional
     * follow-up prompts. This powers the experience shell's conversational AI
     * assistant with context-aware health guidance.</p>
     *
     * @param tenantId      mandatory tenant context (X-Tenant-ID)
     * @param requestId     unique request identifier (X-Request-ID)
     * @param correlationId correlation chain identifier (X-Correlation-ID)
     * @param body          request body containing "question" text and optional "context" map
     * @return structured guidance response with answer, confidence, sources, and follow-ups
     */
    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> askGuidance(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        String question = body.getOrDefault("question", "").toString();
        log.info("Guidance question received: tenant={}, correlationId={}, questionLength={}",
                tenantId, correlationId, question.length());

        // Placeholder: structured guidance response shape
        Map<String, Object> guidance = new LinkedHashMap<>();
        guidance.put("answer", "Based on available health information, I can provide general guidance. "
                + "For specific medical advice, please consult your healthcare provider.");
        guidance.put("confidence", 0.85);
        guidance.put("source", "HEALTH_KNOWLEDGE_BASE");
        guidance.put("category", "GENERAL_HEALTH");
        guidance.put("followUpPrompts", List.of(
                "Would you like to find a provider near you?",
                "Do you want to see related health education materials?",
                "Should I check your upcoming appointments?"
        ));
        guidance.put("references", List.of(
                Map.of("title", "Ministry of Health Guidelines", "type", "GUIDELINE", "url", "/education/moh-guidelines"),
                Map.of("title", "WHO Health Topics", "type", "EXTERNAL", "url", "https://www.who.int/health-topics")
        ));
        guidance.put("disclaimers", List.of(
                "This is general health information and does not constitute medical advice.",
                "Always consult a qualified healthcare professional for diagnosis and treatment."
        ));
        guidance.put("generatedAt", OffsetDateTime.now().toString());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", guidance);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the current user's active reminders and prompts.
     *
     * <p>Reminders include medication schedules, upcoming appointments,
     * vaccination due dates, wellness check-ins, and proactive health
     * prompts driven by the user's context and care plans.</p>
     *
     * @param tenantId      mandatory tenant context (X-Tenant-ID)
     * @param requestId     unique request identifier (X-Request-ID)
     * @param correlationId correlation chain identifier (X-Correlation-ID)
     * @return list of active reminders with type, message, priority, and due timestamps
     */
    @GetMapping("/reminders")
    public ResponseEntity<Map<String, Object>> getReminders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        log.info("Fetching reminders: tenant={}, requestId={}", tenantId, requestId);

        // Placeholder: realistic reminder data shapes
        List<Map<String, Object>> reminders = List.of(
                createReminder("rem-001", "MEDICATION", "Take Metformin 500mg with breakfast",
                        "HIGH", "08:00", true),
                createReminder("rem-002", "APPOINTMENT", "Follow-up appointment with Dr. Moyo at City Clinic",
                        "MEDIUM", null, false),
                createReminder("rem-003", "VACCINATION", "COVID-19 booster dose due",
                        "MEDIUM", null, false),
                createReminder("rem-004", "WELLNESS", "Daily step goal: 2,340 of 8,000 steps completed",
                        "LOW", null, false),
                createReminder("rem-005", "SCREENING", "Annual blood pressure screening recommended",
                        "MEDIUM", null, false)
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", reminders);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "total", reminders.size()
        ));
        return ResponseEntity.ok(response);
    }

    /**
     * Returns health education content relevant to the user's context.
     *
     * <p>Content is curated based on the user's health profile, active conditions,
     * medications, age group, and locale. Supports the person-centered doctrine
     * principle by providing contextually relevant health literacy materials.</p>
     *
     * @param tenantId      mandatory tenant context (X-Tenant-ID)
     * @param requestId     unique request identifier (X-Request-ID)
     * @param correlationId correlation chain identifier (X-Correlation-ID)
     * @param category      optional filter by content category (e.g., NUTRITION, EXERCISE, MENTAL_HEALTH)
     * @param locale        optional locale code for content language (default: en-ZW)
     * @return list of education content items with title, summary, type, and access URLs
     */
    @GetMapping("/education")
    public ResponseEntity<Map<String, Object>> getEducationContent(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "en-ZW") String locale) {

        log.info("Fetching education content: tenant={}, category={}, locale={}", tenantId, category, locale);

        List<Map<String, Object>> content = List.of(
                createEducationItem("edu-001", "Understanding Your Blood Pressure",
                        "Learn how to monitor and manage blood pressure effectively.",
                        "CARDIOVASCULAR", "ARTICLE", "5 min read"),
                createEducationItem("edu-002", "Healthy Eating for Diabetes Management",
                        "Dietary guidelines to help manage blood sugar levels.",
                        "NUTRITION", "GUIDE", "8 min read"),
                createEducationItem("edu-003", "Importance of Sleep for Health",
                        "How quality sleep impacts your physical and mental wellbeing.",
                        "SLEEP", "VIDEO", "12 min watch"),
                createEducationItem("edu-004", "Childhood Immunization Schedule",
                        "Complete vaccination schedule recommended by the Ministry of Health.",
                        "IMMUNIZATION", "INFOGRAPHIC", "3 min read"),
                createEducationItem("edu-005", "Mental Health and Stress Management",
                        "Practical techniques for managing stress and improving mental wellness.",
                        "MENTAL_HEALTH", "INTERACTIVE", "10 min")
        );

        // Apply category filter if provided
        List<Map<String, Object>> filtered = category != null
                ? content.stream().filter(c -> category.equalsIgnoreCase((String) c.get("category"))).toList()
                : content;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", filtered);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "locale", locale,
                "total", filtered.size()
        ));
        return ResponseEntity.ok(response);
    }

    /**
     * Federated search across health, wellness, diet, sleep, service, and marketplace domains.
     *
     * <p>Powers the experience shell's unified search bar. Results are grouped by domain
     * and ranked by relevance. This implements the doctrine requirement for a searchable,
     * context-aware experience shell that spans all Health OS capabilities.</p>
     *
     * @param tenantId      mandatory tenant context (X-Tenant-ID)
     * @param requestId     unique request identifier (X-Request-ID)
     * @param correlationId correlation chain identifier (X-Correlation-ID)
     * @param q             search query text
     * @param domain        optional domain filter (HEALTH, WELLNESS, DIET, SLEEP, SERVICE, MARKETPLACE)
     * @param limit         maximum results per domain (default 5)
     * @return federated search results grouped by domain with relevance scores
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> federatedSearch(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam String q,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false, defaultValue = "5") int limit) {

        log.info("Federated search: tenant={}, query='{}', domain={}, limit={}",
                tenantId, q, domain, limit);

        // Placeholder: federated results across domains
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("query", q);
        results.put("totalResults", 12);
        results.put("domains", Map.of(
                "HEALTH", List.of(
                        createSearchResult("Health ID Card", "Your digital health identity card", "HEALTH", 0.95, "/my-life/health-id"),
                        createSearchResult("Blood Pressure Log", "Recent blood pressure readings", "HEALTH", 0.82, "/wellness/vitals")
                ),
                "WELLNESS", List.of(
                        createSearchResult("Step Counter", "Daily activity and step tracking", "WELLNESS", 0.88, "/wellness/activities"),
                        createSearchResult("Sleep Tracker", "Sleep quality and duration history", "WELLNESS", 0.75, "/wellness/sleep")
                ),
                "DIET", List.of(
                        createSearchResult("Meal Planner", "Personalized meal planning and nutrition", "DIET", 0.72, "/wellness/diet/planner")
                ),
                "SLEEP", List.of(
                        createSearchResult("Sleep Report", "Weekly sleep quality analysis", "SLEEP", 0.70, "/wellness/sleep/report")
                ),
                "SERVICE", List.of(
                        createSearchResult("Find a Doctor", "Search for healthcare providers near you", "SERVICE", 0.90, "/services/discover"),
                        createSearchResult("Book Appointment", "Schedule a visit with your provider", "SERVICE", 0.85, "/appointments/new")
                ),
                "MARKETPLACE", List.of(
                        createSearchResult("Pharmacy Services", "Order medications and health products", "MARKETPLACE", 0.80, "/marketplace/pharmacy"),
                        createSearchResult("Lab Tests", "Book laboratory tests online", "MARKETPLACE", 0.77, "/marketplace/labs")
                )
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", results);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "search_time_ms", 45
        ));
        return ResponseEntity.ok(response);
    }

    // -- Helper methods -------------------------------------------------------

    private Map<String, Object> createReminder(String id, String type, String message,
                                                String priority, String timeOfDay, boolean recurring) {
        Map<String, Object> reminder = new LinkedHashMap<>();
        reminder.put("id", id);
        reminder.put("type", type);
        reminder.put("message", message);
        reminder.put("priority", priority);
        reminder.put("status", "ACTIVE");
        if (timeOfDay != null) {
            reminder.put("timeOfDay", timeOfDay);
        }
        reminder.put("recurring", recurring);
        reminder.put("createdAt", OffsetDateTime.now().minusDays(7).toString());
        return reminder;
    }

    private Map<String, Object> createEducationItem(String id, String title, String summary,
                                                     String category, String contentType, String duration) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("title", title);
        item.put("summary", summary);
        item.put("category", category);
        item.put("contentType", contentType);
        item.put("duration", duration);
        item.put("locale", "en-ZW");
        item.put("url", "/education/" + id);
        item.put("publishedAt", OffsetDateTime.now().minusDays(30).toString());
        return item;
    }

    private Map<String, Object> createSearchResult(String title, String description,
                                                    String domain, double relevance, String path) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", title);
        result.put("description", description);
        result.put("domain", domain);
        result.put("relevance", relevance);
        result.put("path", path);
        return result;
    }
}
