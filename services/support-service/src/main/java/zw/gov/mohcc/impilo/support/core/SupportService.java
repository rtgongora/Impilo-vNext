package zw.gov.mohcc.impilo.support.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.support.api.dto.*;
import zw.gov.mohcc.impilo.support.domain.*;
import zw.gov.mohcc.impilo.support.repository.*;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class SupportService {

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);
    private static final String PRODUCER = "support-service";

    private final TicketRepository ticketRepository;
    private final ArticleRepository articleRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SupportService(TicketRepository ticketRepository, ArticleRepository articleRepository,
                           OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
        this.ticketRepository = ticketRepository;
        this.articleRepository = articleRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TicketEntity createTicket(UUID tenantId, String podId, String correlationId,
                                      String idempotencyKey, CreateTicketRequest request) {
        UUID ticketId = UUID.randomUUID();
        TicketEntity ticket = new TicketEntity(ticketId, tenantId, request.title(),
                request.description(), request.reporterRef());
        if (request.category() != null) ticket.setCategory(request.category());
        if (request.priority() != null) ticket.setPriority(request.priority());
        if (request.facilityRef() != null) ticket.setFacilityRef(request.facilityRef());
        ticketRepository.save(ticket);

        Map<String, Object> payload = buildTicketState(ticket);
        appendOutboxEvent("impilo.support.ticket.created.v1", "Ticket", ticketId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, ticketId.toString(), "Ticket");

        log.info("Created ticket [ticketId={}, title={}]", ticketId, request.title());
        return ticket;
    }

    @Transactional
    public TicketEntity updateTicket(UUID ticketId, UUID tenantId, String podId, String correlationId,
                                      String idempotencyKey, UpdateTicketRequest request) {
        TicketEntity ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));

        if (request.title() != null) ticket.setTitle(request.title());
        if (request.description() != null) ticket.setDescription(request.description());
        if (request.category() != null) ticket.setCategory(request.category());
        if (request.priority() != null) ticket.setPriority(request.priority());
        if (request.assigneeRef() != null) ticket.setAssigneeRef(request.assigneeRef());

        if (request.status() != null) {
            ticket.setStatus(request.status());
            if ("RESOLVED".equals(request.status()) || "CLOSED".equals(request.status())) {
                ticket.setResolvedAt(OffsetDateTime.now());
            }
        }
        if (request.resolution() != null) ticket.setResolution(request.resolution());

        ticket.setVersion(ticket.getVersion() + 1);
        ticket.setUpdatedAt(OffsetDateTime.now());
        ticketRepository.save(ticket);

        Map<String, Object> payload = buildTicketState(ticket);
        appendOutboxEvent("impilo.support.ticket.updated.v1", "Ticket", ticketId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, ticketId.toString(), "Ticket");

        log.info("Updated ticket [ticketId={}, status={}]", ticketId, ticket.getStatus());
        return ticket;
    }

    @Transactional(readOnly = true)
    public Optional<TicketEntity> getTicket(UUID ticketId) { return ticketRepository.findById(ticketId); }

    @Transactional(readOnly = true)
    public Page<TicketEntity> listTickets(UUID tenantId, String status, String priority, int page, int size) {
        return ticketRepository.findFiltered(tenantId, status, priority, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<TicketEntity> getTicketSnapshot(OffsetDateTime asOf, int page, int size) {
        return ticketRepository.findSnapshotAsOf(asOf, PageRequest.of(page, size));
    }

    @Transactional
    public KnowledgeArticleEntity createArticle(UUID tenantId, String podId, String correlationId,
                                                  String idempotencyKey, CreateArticleRequest request) {
        UUID articleId = UUID.randomUUID();
        KnowledgeArticleEntity article = new KnowledgeArticleEntity(articleId, tenantId,
                request.title(), request.body(), request.authorRef());
        if (request.category() != null) article.setCategory(request.category());
        if (request.tags() != null) article.setTags(request.tags());
        articleRepository.save(article);

        Map<String, Object> payload = buildArticleState(article);
        appendOutboxEvent("impilo.support.article.created.v1", "Article", articleId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, articleId.toString(), "Article");

        log.info("Created article [articleId={}, title={}]", articleId, request.title());
        return article;
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeArticleEntity> getArticle(UUID articleId) { return articleRepository.findById(articleId); }

    @Transactional(readOnly = true)
    public Page<KnowledgeArticleEntity> listArticles(UUID tenantId, String category, String status, int page, int size) {
        return articleRepository.findFiltered(tenantId, category, status, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<KnowledgeArticleEntity> getArticleSnapshot(OffsetDateTime asOf, int page, int size) {
        return articleRepository.findSnapshotAsOf(asOf, PageRequest.of(page, size));
    }

    private void appendOutboxEvent(String eventType, String aggregateType, String aggregateId,
                                    UUID tenantId, String podId, String correlationId,
                                    String idempotencyKey, Map<String, Object> payload,
                                    String partitionKey, String subjectType) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setCorrelationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setCausationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(aggregateId);
        outbox.setSubjectType(subjectType);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(partitionKey);
        outboxRepository.save(outbox);
    }

    private Map<String, Object> buildTicketState(TicketEntity t) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("ticket_id", t.getTicketId().toString()); s.put("title", t.getTitle());
        s.put("category", t.getCategory()); s.put("priority", t.getPriority());
        s.put("status", t.getStatus()); s.put("reporter_ref", t.getReporterRef());
        s.put("assignee_ref", t.getAssigneeRef()); s.put("facility_ref", t.getFacilityRef());
        return s;
    }

    private Map<String, Object> buildArticleState(KnowledgeArticleEntity a) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("article_id", a.getArticleId().toString()); s.put("title", a.getTitle());
        s.put("category", a.getCategory()); s.put("status", a.getStatus());
        s.put("author_ref", a.getAuthorRef());
        return s;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException("JSON serialization failed", e); }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String msg) { super(msg); }
    }
}
