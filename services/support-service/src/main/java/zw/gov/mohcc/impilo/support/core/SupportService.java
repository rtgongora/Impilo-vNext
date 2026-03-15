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
    private final CommentRepository commentRepository;
    private final AssignmentRepository assignmentRepository;
    private final SupportMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public SupportService(TicketRepository ticketRepository, ArticleRepository articleRepository,
                           OutboxEventRepository outboxRepository, CommentRepository commentRepository,
                           AssignmentRepository assignmentRepository, SupportMessageRepository messageRepository,
                           ObjectMapper objectMapper) {
        this.ticketRepository = ticketRepository;
        this.articleRepository = articleRepository;
        this.outboxRepository = outboxRepository;
        this.commentRepository = commentRepository;
        this.assignmentRepository = assignmentRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    // ── Tickets ──────────────────────────────────────────────────────────

    @Transactional
    public TicketEntity createTicket(UUID tenantId, String podId, String correlationId,
                                      String requestId, String idempotencyKey, CreateTicketRequest request) {
        UUID ticketId = UUID.randomUUID();
        TicketEntity ticket = new TicketEntity(ticketId, tenantId, request.title(),
                request.description(), request.reporterRef());
        if (request.category() != null) ticket.setCategory(request.category());
        if (request.priority() != null) ticket.setPriority(request.priority());
        if (request.facilityRef() != null) ticket.setFacilityRef(request.facilityRef());
        if (requestId != null) ticket.setRequestId(requestId);
        if (correlationId != null) ticket.setCorrelationId(UUID.fromString(correlationId));
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
    public Page<TicketEntity> listTickets(UUID tenantId, String status, String priority,
                                           String category, String assigneeRef, int page, int size) {
        return ticketRepository.findFiltered(tenantId, status, priority, category, assigneeRef,
                PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<TicketEntity> getTicketSnapshot(OffsetDateTime asOf, int page, int size) {
        return ticketRepository.findSnapshotAsOf(asOf, PageRequest.of(page, size));
    }

    // ── Escalation ───────────────────────────────────────────────────────

    @Transactional
    public TicketEntity escalateTicket(UUID ticketId, UUID tenantId, String podId, String correlationId,
                                        String idempotencyKey, String escalatedBy, int targetLevel) {
        TicketEntity ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));

        ticket.setEscalationLevel(targetLevel);
        ticket.setEscalatedAt(OffsetDateTime.now());
        ticket.setEscalatedBy(escalatedBy);
        if (targetLevel >= 3) {
            ticket.setPriority("CRITICAL");
        }
        ticket.setVersion(ticket.getVersion() + 1);
        ticket.setUpdatedAt(OffsetDateTime.now());
        ticketRepository.save(ticket);

        Map<String, Object> payload = buildTicketState(ticket);
        payload.put("escalation_level", targetLevel);
        payload.put("escalated_by", escalatedBy);
        appendOutboxEvent("impilo.support.ticket.escalated.v1", "Ticket", ticketId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, ticketId.toString(), "Ticket");

        log.info("Escalated ticket [ticketId={}, level={}]", ticketId, targetLevel);
        return ticket;
    }

    // ── Comments ─────────────────────────────────────────────────────────

    @Transactional
    public CommentEntity addComment(UUID ticketId, UUID tenantId, String podId, String correlationId,
                                     String idempotencyKey, String authorRef, String body) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new NotFoundException("Ticket not found: " + ticketId);
        }
        UUID commentId = UUID.randomUUID();
        CommentEntity comment = new CommentEntity(commentId, ticketId, tenantId, authorRef, body);
        commentRepository.save(comment);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("comment_id", commentId.toString());
        payload.put("ticket_id", ticketId.toString());
        payload.put("author_ref", authorRef);
        payload.put("body", body);
        appendOutboxEvent("impilo.support.ticket.comment.added.v1", "Ticket", ticketId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, ticketId.toString(), "Ticket");

        log.info("Added comment [commentId={}, ticketId={}]", commentId, ticketId);
        return comment;
    }

    @Transactional(readOnly = true)
    public Page<CommentEntity> listComments(UUID ticketId, int page, int size) {
        return commentRepository.findByTicketId(ticketId, PageRequest.of(page, size));
    }

    // ── Assignments ──────────────────────────────────────────────────────

    @Transactional
    public AssignmentEntity assignTicket(UUID ticketId, UUID tenantId, String podId, String correlationId,
                                          String idempotencyKey, String assigneeRef, String assignedBy) {
        TicketEntity ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));

        // Close previous assignment
        List<AssignmentEntity> previous = assignmentRepository.findByTicketIdOrderByAssignedAtDesc(ticketId);
        if (!previous.isEmpty()) {
            AssignmentEntity prev = previous.get(0);
            if (prev.getUnassignedAt() == null) {
                prev.setUnassignedAt(OffsetDateTime.now());
                assignmentRepository.save(prev);
            }
        }

        UUID assignmentId = UUID.randomUUID();
        AssignmentEntity assignment = new AssignmentEntity(assignmentId, ticketId, tenantId, assigneeRef, assignedBy);
        assignmentRepository.save(assignment);

        ticket.setAssigneeRef(assigneeRef);
        ticket.setVersion(ticket.getVersion() + 1);
        ticket.setUpdatedAt(OffsetDateTime.now());
        ticketRepository.save(ticket);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignment_id", assignmentId.toString());
        payload.put("ticket_id", ticketId.toString());
        payload.put("assignee_ref", assigneeRef);
        payload.put("assigned_by", assignedBy);
        appendOutboxEvent("impilo.support.ticket.assigned.v1", "Ticket", ticketId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, ticketId.toString(), "Ticket");

        log.info("Assigned ticket [ticketId={}, assigneeRef={}]", ticketId, assigneeRef);
        return assignment;
    }

    @Transactional(readOnly = true)
    public Page<AssignmentEntity> listAssignments(UUID ticketId, int page, int size) {
        return assignmentRepository.findByTicketId(ticketId, PageRequest.of(page, size));
    }

    // ── Messages ─────────────────────────────────────────────────────────

    @Transactional
    public SupportMessageEntity sendMessage(UUID ticketId, UUID tenantId, String podId, String correlationId,
                                             String idempotencyKey, String senderRef, String senderType, String body) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new NotFoundException("Ticket not found: " + ticketId);
        }
        UUID messageId = UUID.randomUUID();
        SupportMessageEntity message = new SupportMessageEntity(messageId, ticketId, tenantId, senderRef, senderType, body);
        messageRepository.save(message);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message_id", messageId.toString());
        payload.put("ticket_id", ticketId.toString());
        payload.put("sender_ref", senderRef);
        payload.put("sender_type", senderType);
        payload.put("body", body);
        appendOutboxEvent("impilo.support.message.sent.v1", "Ticket", ticketId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, ticketId.toString(), "Ticket");

        log.info("Sent message [messageId={}, ticketId={}]", messageId, ticketId);
        return message;
    }

    @Transactional(readOnly = true)
    public Page<SupportMessageEntity> listMessages(UUID ticketId, int page, int size) {
        return messageRepository.findByTicketId(ticketId, PageRequest.of(page, size));
    }

    // ── Articles ─────────────────────────────────────────────────────────

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

    @Transactional
    public KnowledgeArticleEntity updateArticle(UUID articleId, UUID tenantId, String podId,
                                                  String correlationId, String idempotencyKey,
                                                  UpdateArticleRequest request) {
        KnowledgeArticleEntity article = articleRepository.findById(articleId)
                .orElseThrow(() -> new NotFoundException("Article not found: " + articleId));

        if (request.title() != null) article.setTitle(request.title());
        if (request.body() != null) article.setBody(request.body());
        if (request.category() != null) article.setCategory(request.category());
        if (request.tags() != null) article.setTags(request.tags());
        if (request.status() != null) {
            article.setStatus(request.status());
            if ("PUBLISHED".equals(request.status()) && article.getPublishedAt() == null) {
                article.setPublishedAt(OffsetDateTime.now());
            }
        }
        article.setVersion(article.getVersion() + 1);
        article.setUpdatedAt(OffsetDateTime.now());
        articleRepository.save(article);

        Map<String, Object> payload = buildArticleState(article);
        appendOutboxEvent("impilo.support.article.updated.v1", "Article", articleId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, articleId.toString(), "Article");

        log.info("Updated article [articleId={}, title={}]", articleId, article.getTitle());
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

    // ── Dashboard ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats(UUID tenantId) {
        long openCount = ticketRepository.countByTenantIdAndStatus(tenantId, "OPEN");
        long inProgressCount = ticketRepository.countByTenantIdAndStatus(tenantId, "IN_PROGRESS");
        long resolvedCount = ticketRepository.countByTenantIdAndStatus(tenantId, "RESOLVED");
        long closedCount = ticketRepository.countByTenantIdAndStatus(tenantId, "CLOSED");
        long criticalCount = ticketRepository.countByTenantIdAndPriority(tenantId, "CRITICAL");
        long highCount = ticketRepository.countByTenantIdAndPriority(tenantId, "HIGH");
        long escalatedCount = ticketRepository.countByTenantIdAndEscalationLevelGreaterThan(tenantId, 0);
        double avgResolutionHrs = ticketRepository.avgResolutionHours(tenantId);

        return new DashboardStatsResponse(openCount, inProgressCount, resolvedCount, closedCount,
                criticalCount, highCount, escalatedCount, avgResolutionHrs);
    }

    // ── Outbox helper ────────────────────────────────────────────────────

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
        if (t.getRequestId() != null) s.put("request_id", t.getRequestId());
        if (t.getCorrelationId() != null) s.put("correlation_id", t.getCorrelationId().toString());
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
