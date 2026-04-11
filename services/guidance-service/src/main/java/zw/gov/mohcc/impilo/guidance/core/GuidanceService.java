package zw.gov.mohcc.impilo.guidance.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.guidance.persistence.entity.KnowledgeArticleEntity;
import zw.gov.mohcc.impilo.guidance.persistence.entity.ReminderEntity;
import zw.gov.mohcc.impilo.guidance.persistence.repository.KnowledgeArticleRepository;
import zw.gov.mohcc.impilo.guidance.persistence.repository.ReminderRepository;

import java.util.*;

/**
 * Core guidance logic — Health OS §13 (Conversational and Guidance Services).
 *
 * <p>Handles knowledge retrieval, reminder management, and conversational
 * guidance responses. The conversational ask endpoint currently uses
 * knowledge-article search as its response source; future versions will
 * integrate with an LLM orchestration layer.</p>
 */
@Service
public class GuidanceService {

    private static final Logger log = LoggerFactory.getLogger(GuidanceService.class);

    private final KnowledgeArticleRepository articleRepo;
    private final ReminderRepository reminderRepo;

    public GuidanceService(KnowledgeArticleRepository articleRepo, ReminderRepository reminderRepo) {
        this.articleRepo = articleRepo;
        this.reminderRepo = reminderRepo;
    }

    /** Conversational ask — search knowledge base and compose a response. */
    public Map<String, Object> ask(String tenantId, String actorId, String question, boolean personalized) {
        log.info("Guidance ask: tenant={} actor={} personalized={} q={}", tenantId, actorId, personalized, question.length());

        // Search knowledge base for relevant articles
        Page<KnowledgeArticleEntity> articles = articleRepo.search(tenantId, question, PageRequest.of(0, 5));

        List<Map<String, String>> sources = articles.getContent().stream()
                .map(a -> Map.of("title", a.getTitle(), "type", a.getDomain(), "url", a.getSourceUrl() != null ? a.getSourceUrl() : ""))
                .toList();

        String responseText;
        double confidence;
        if (!articles.isEmpty()) {
            KnowledgeArticleEntity top = articles.getContent().get(0);
            responseText = top.getSummary();
            confidence = 0.85;
        } else {
            responseText = "I don't have specific information about that topic yet. Please consult your healthcare provider for personalized advice.";
            confidence = 0.3;
        }

        List<String> followUps = List.of(
                "Would you like to know more about this topic?",
                "Should I find related health services near you?"
        );

        return Map.of(
                "response", responseText,
                "confidence", confidence,
                "sources", sources,
                "followUpPrompts", followUps,
                "personalized", personalized
        );
    }

    /** Get active reminders for a user. */
    public List<ReminderEntity> getReminders(String tenantId, String actorId) {
        return reminderRepo.findByTenantIdAndActorIdAndStatusOrderByDueDateAsc(tenantId, actorId, "ACTIVE");
    }

    /** Get education content for a user's context. */
    public Page<KnowledgeArticleEntity> getEducation(String tenantId, String domain, int page, int size) {
        if (domain != null && !domain.equals("all")) {
            return articleRepo.findByTenantIdAndDomainAndStatusOrderByUpdatedAtDesc(tenantId, domain, "PUBLISHED", PageRequest.of(page, size));
        }
        return articleRepo.findByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId, "PUBLISHED", PageRequest.of(page, size));
    }

    /** Search knowledge articles. */
    public Page<KnowledgeArticleEntity> search(String tenantId, String query, int page, int size) {
        return articleRepo.search(tenantId, query, PageRequest.of(page, size));
    }
}
