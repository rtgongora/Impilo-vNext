package zw.gov.mohcc.impilo.auditledger.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.auditledger.api.dto.AppendAuditRecordRequest;
import zw.gov.mohcc.impilo.auditledger.domain.*;
import zw.gov.mohcc.impilo.auditledger.repository.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class AuditLedgerService {

    private static final Logger log = LoggerFactory.getLogger(AuditLedgerService.class);
    private static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private final AuditRecordRepository recordRepository;
    private final ChainHeadRepository chainHeadRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AuditLedgerService(AuditRecordRepository recordRepository, ChainHeadRepository chainHeadRepository,
                               OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
        this.recordRepository = recordRepository;
        this.chainHeadRepository = chainHeadRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditRecordEntity appendRecord(UUID tenantId, String podId, String correlationIdCtx,
                                            String idempotencyKey, AppendAuditRecordRequest request) {
        // Get or create chain head for tenant
        ChainHeadEntity head = chainHeadRepository.findById(tenantId)
                .orElse(new ChainHeadEntity(tenantId, GENESIS_HASH, 0));

        long nextSeq = head.getCurrentSeq() + 1;
        String prevHash = head.getCurrentHash();

        UUID recordId = UUID.randomUUID();
        String detailJson = toJson(request.detail());
        String outcome = request.outcome() != null ? request.outcome() : "SUCCESS";

        // Compute SHA-256 hash: H(prev_hash || tenant_id || seq || action || actor_id || resource || occurred_at)
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String entryHash = computeHash(prevHash, tenantId, nextSeq, request.action(),
                request.actorId(), request.resourceType(), occurredAt);

        AuditRecordEntity record = new AuditRecordEntity(recordId, tenantId, nextSeq,
                request.correlationId(), request.actorId(), request.actorType(),
                request.action(), request.resourceType(), request.resourceId(),
                outcome, detailJson, occurredAt, prevHash, entryHash);

        recordRepository.save(record);

        // Advance chain head
        head.setCurrentHash(entryHash);
        head.setCurrentSeq(nextSeq);
        head.setUpdatedAt(OffsetDateTime.now());
        chainHeadRepository.save(head);

        // Emit event
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("record_id", recordId.toString());
        payload.put("tenant_id", tenantId.toString());
        payload.put("sequence_num", nextSeq);
        payload.put("action", request.action());
        payload.put("actor_id", request.actorId());
        payload.put("resource_type", request.resourceType());
        payload.put("entry_hash", entryHash);

        appendOutboxEvent("impilo.audit.record.appended.v1", recordId.toString(),
                tenantId, podId, correlationIdCtx, idempotencyKey, payload, tenantId.toString());

        log.info("Appended audit record [recordId={}, seq={}, action={}]", recordId, nextSeq, request.action());
        return record;
    }

    @Transactional(readOnly = true)
    public List<AuditRecordEntity> queryByCorrelationId(UUID correlationId) {
        return recordRepository.findByCorrelationId(correlationId);
    }

    @Transactional(readOnly = true)
    public Optional<AuditRecordEntity> getRecord(UUID recordId) {
        return recordRepository.findByRecordId(recordId);
    }

    @Transactional(readOnly = true)
    public Page<AuditRecordEntity> listRecords(UUID tenantId, int page, int size) {
        return recordRepository.findByTenantOrdered(tenantId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public boolean verifyChainIntegrity(UUID tenantId, long fromSeq, long toSeq) {
        List<AuditRecordEntity> records = recordRepository.findChainRange(tenantId, fromSeq, toSeq);
        if (records.isEmpty()) return true;

        for (int i = 0; i < records.size(); i++) {
            AuditRecordEntity r = records.get(i);
            String expectedHash = computeHash(r.getPrevHash(), r.getTenantId(), r.getSequenceNum(),
                    r.getAction(), r.getActorId(), r.getResourceType(), r.getOccurredAt());
            if (!expectedHash.equals(r.getEntryHash())) {
                log.error("Chain integrity violation at seq={} for tenant={}", r.getSequenceNum(), tenantId);
                return false;
            }
            if (i > 0 && !records.get(i - 1).getEntryHash().equals(r.getPrevHash())) {
                log.error("Chain link broken at seq={} for tenant={}", r.getSequenceNum(), tenantId);
                return false;
            }
        }
        return true;
    }

    private String computeHash(String prevHash, UUID tenantId, long seq, String action,
                                String actorId, String resourceType, OffsetDateTime occurredAt) {
        String input = prevHash + "|" + tenantId + "|" + seq + "|" + action + "|"
                + actorId + "|" + resourceType + "|" + occurredAt.toInstant().toEpochMilli();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void appendOutboxEvent(String eventType, String aggregateId, UUID tenantId,
                                    String podId, String correlationId, String idempotencyKey,
                                    Map<String, Object> payload, String partitionKey) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("AuditRecord");
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setCorrelationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setCausationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(aggregateId);
        outbox.setSubjectType("AuditRecord");
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(partitionKey);
        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException("JSON serialization failed", e); }
    }
}
