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
import zw.gov.mohcc.impilo.guidance.llm.GuidanceLlmClient;

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
    private final GuidanceLlmClient llmClient;

    public GuidanceService(KnowledgeArticleRepository articleRepo, ReminderRepository reminderRepo,
                           GuidanceLlmClient llmClient) {
        this.articleRepo = articleRepo;
        this.reminderRepo = reminderRepo;
        this.llmClient = llmClient;
    }

    /** Conversational ask — search knowledge base and compose a response. */
    public Map<String, Object> ask(String tenantId, String actorId, String question, boolean personalized) {
        log.info("Guidance ask: tenant={} actor={} personalized={} q={}", tenantId, actorId, personalized, question.length());

        // Search knowledge base for relevant articles. The repository search is a full-phrase
        // LIKE over title/summary, which a natural-language question ("How do I prevent
        // cholera?") never matches — so fall back to per-keyword retrieval over the question's
        // significant words, keeping first-hit order and de-duplicating.
        Page<KnowledgeArticleEntity> articles = articleRepo.search(tenantId, question, PageRequest.of(0, 5));
        if (articles.isEmpty()) {
            Map<UUID, KnowledgeArticleEntity> byId = new LinkedHashMap<>();
            for (String keyword : significantKeywords(question)) {
                for (KnowledgeArticleEntity a :
                        articleRepo.search(tenantId, keyword, PageRequest.of(0, 5)).getContent()) {
                    byId.putIfAbsent(a.getId(), a);
                }
                if (byId.size() >= 5) break;
            }
            if (!byId.isEmpty()) {
                articles = new org.springframework.data.domain.PageImpl<>(
                        new ArrayList<>(byId.values()).subList(0, Math.min(5, byId.size())));
            }
        }

        List<Map<String, String>> sources = articles.getContent().stream()
                .map(a -> Map.of("title", a.getTitle(), "type", a.getDomain(), "url", a.getSourceUrl() != null ? a.getSourceUrl() : ""))
                .toList();

        // Grounded LLM synthesis over the retrieved articles (G039); falls back to retrieval when
        // the LLM is disabled/unavailable or no real provider answered (honesty gate).
        List<GuidanceLlmClient.Source> llmSources = articles.getContent().stream()
                .map(a -> new GuidanceLlmClient.Source(a.getTitle(), a.getSummary(), a.getDomain()))
                .toList();
        Optional<GuidanceLlmClient.GroundedAnswer> grounded = llmClient.synthesize(question, llmSources);

        String responseText;
        double confidence;
        String answerSource;
        if (grounded.isPresent()) {
            responseText = grounded.get().answer();
            confidence = grounded.get().confidence();
            answerSource = "llm";
        } else if (!articles.isEmpty()) {
            responseText = articles.getContent().get(0).getSummary();
            confidence = 0.85;
            answerSource = "retrieval";
        } else {
            responseText = "I don't have specific information about that topic yet. Please consult your healthcare provider for personalized advice.";
            confidence = 0.3;
            answerSource = "none";
        }

        List<String> followUps = List.of(
                "Would you like to know more about this topic?",
                "Should I find related health services near you?"
        );

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("response", responseText);
        response.put("confidence", confidence);
        response.put("answerSource", answerSource);
        response.put("sources", sources);
        response.put("followUpPrompts", followUps);
        response.put("personalized", personalized);
        return response;
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

    /** Get published education content for one category (public health-info taxonomy). */
    public Page<KnowledgeArticleEntity> getEducationByCategory(String tenantId, String category, int page, int size) {
        return articleRepo.findByTenantIdAndCategoryAndStatusOrderByUpdatedAtDesc(
                tenantId, category, "PUBLISHED", PageRequest.of(page, size));
    }

    /** Read one PUBLISHED article (tenant-scoped; drafts/retired content never leak). */
    public Optional<KnowledgeArticleEntity> getPublishedArticle(String tenantId, UUID id) {
        return articleRepo.findByIdAndTenantIdAndStatus(id, tenantId, "PUBLISHED");
    }

    /** Published-article counts per category — the public topic index. */
    public List<KnowledgeArticleRepository.CategoryCount> getEducationCategories(String tenantId) {
        return articleRepo.countPublishedByCategory(tenantId);
    }

    /** Search knowledge articles. */
    public Page<KnowledgeArticleEntity> search(String tenantId, String query, int page, int size) {
        return articleRepo.search(tenantId, query, PageRequest.of(page, size));
    }

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "with", "what", "when", "where", "which", "should",
            "would", "could", "how", "do", "does", "did", "can", "is", "are", "was",
            "were", "a", "an", "of", "in", "on", "to", "my", "your", "their", "his",
            "her", "its", "our", "you", "i", "we", "they", "it", "this", "that",
            "there", "have", "has", "had", "be", "been", "being", "while", "during",
            "about", "help", "please", "need", "want", "get", "make");

    /** Significant words (4+ chars, not stopwords) from a natural-language question. */
    static List<String> significantKeywords(String question) {
        if (question == null || question.isBlank()) return List.of();
        List<String> keywords = new ArrayList<>();
        for (String token : question.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 4 && !STOPWORDS.contains(token) && !keywords.contains(token)) {
                keywords.add(token);
            }
        }
        return keywords;
    }
}
