package zw.gov.mohcc.impilo.guidance.api;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.guidance.core.GuidanceService;
import zw.gov.mohcc.impilo.guidance.events.GuidanceOutboxAppender;
import zw.gov.mohcc.impilo.guidance.persistence.entity.KnowledgeArticleEntity;
import zw.gov.mohcc.impilo.guidance.persistence.entity.ReminderEntity;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Guidance Service API — Health OS §13.
 *
 * <p>Internal endpoints consumed by the Experience BFF GuidanceServiceClient.
 * Not exposed directly to the UI.</p>
 */
@RestController
@RequestMapping("/internal/v1/guidance")
public class GuidanceApiController {

    private final GuidanceService guidanceService;
    private final GuidanceOutboxAppender outboxAppender;

    public GuidanceApiController(GuidanceService guidanceService, GuidanceOutboxAppender outboxAppender) {
        this.guidanceService = guidanceService;
        this.outboxAppender = outboxAppender;
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(
            @RequestHeader("x-tenant-id") String tenantId,
            @RequestHeader("x-actor-id") String actorId,
            @RequestBody Map<String, Object> body) {

        String question = (String) body.getOrDefault("question", "");
        boolean personalized = Boolean.TRUE.equals(body.get("personalized"));

        Map<String, Object> result = guidanceService.ask(tenantId, actorId, question, personalized);
        outboxAppender.recordAskCompleted(tenantId, actorId, question, personalized, result);
        return ResponseEntity.ok(Map.of("data", result));
    }

    @GetMapping("/reminders")
    public ResponseEntity<Map<String, Object>> reminders(
            @RequestHeader("x-tenant-id") String tenantId,
            @RequestHeader("x-actor-id") String actorId) {

        List<ReminderEntity> reminders = guidanceService.getReminders(tenantId, actorId);
        List<Map<String, Object>> data = reminders.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId().toString());
            m.put("type", r.getReminderType());
            m.put("title", r.getTitle());
            m.put("description", r.getDescription());
            m.put("dueDate", r.getDueDate() != null ? r.getDueDate().toString() : null);
            m.put("priority", r.getPriority());
            m.put("dismissed", "DISMISSED".equals(r.getStatus()));
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("data", data));
    }

    @GetMapping("/education")
    public ResponseEntity<Map<String, Object>> education(
            @RequestHeader("x-tenant-id") String tenantId,
            @RequestParam(defaultValue = "all") String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<KnowledgeArticleEntity> articles = guidanceService.getEducation(tenantId, domain, page, size);
        List<Map<String, Object>> data = articles.getContent().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId().toString());
            m.put("title", a.getTitle());
            m.put("summary", a.getSummary());
            m.put("category", a.getCategory());
            m.put("domain", a.getDomain());
            m.put("source", a.getSource());
            m.put("url", a.getSourceUrl());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("data", data));
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestHeader("x-tenant-id") String tenantId,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<KnowledgeArticleEntity> results = guidanceService.search(tenantId, q, page, size);
        List<Map<String, Object>> data = results.getContent().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId().toString());
            m.put("title", a.getTitle());
            m.put("snippet", a.getSummary());
            m.put("domain", a.getDomain());
            m.put("type", a.getCategory());
            m.put("score", 1.0);
            m.put("url", a.getSourceUrl());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("data", data));
    }
}
