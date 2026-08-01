package zw.gov.mohcc.impilo.tshepo.audit.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditEventRequest;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.ChainVerificationResponse;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditChainHeadEntity;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditEventEntity;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.repository.AuditChainHeadRepository;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.repository.AuditEventRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core service for appending audit events to the tamper-evident hash chain.
 *
 * Within a single @Transactional boundary:
 * 1. Lock the chain head for the tenant (PESSIMISTIC_WRITE)
 * 2. Increment the sequence number
 * 3. Compute SHA-256(previousHash + tenantId + eventType + actorId + action + outcome + sequenceNumber + timestamp)
 * 4. Store the event with the computed hash
 * 5. Update the chain head with the new hash and sequence number
 */
@Service
public class AuditChainService {

    private static final Logger log = LoggerFactory.getLogger(AuditChainService.class);
    private static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final int POSTGRES_TIMESTAMP_ROUNDING_MIN_NANOS = -500;
    private static final int POSTGRES_TIMESTAMP_ROUNDING_MAX_NANOS = 499;

    private final AuditEventRepository eventRepository;
    private final AuditChainHeadRepository chainHeadRepository;
    private final ObjectMapper objectMapper;

    public AuditChainService(AuditEventRepository eventRepository,
                              AuditChainHeadRepository chainHeadRepository,
                              ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.chainHeadRepository = chainHeadRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Append an audit event to the hash chain for the given tenant.
     * This method acquires a PESSIMISTIC_WRITE lock on the chain head
     * to ensure gapless sequencing.
     *
     * @param request the audit event to append
     * @return the persisted audit event entity
     */
    @Transactional
    public AuditEventEntity appendEvent(AuditEventRequest request) {
        UUID tenantId = request.tenantId();
        // PostgreSQL stores timestamps at microsecond precision. Hash the same precision that is
        // persisted so a later integrity check sees the exact timestamp input.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // Step 1: Lock and retrieve (or create) the chain head
        AuditChainHeadEntity chainHead = chainHeadRepository.findByTenantIdForUpdate(tenantId)
                .orElseGet(() -> createGenesisChainHead(tenantId));

        // Step 2: Increment sequence number
        long newSequence = chainHead.getSequenceNumber() + 1;
        String previousHash = chainHead.getCurrentHash();

        // Step 3: Compute entry hash
        String entryHash = computeHash(
                previousHash,
                tenantId.toString(),
                request.eventType(),
                request.actorId(),
                request.action(),
                request.outcome(),
                String.valueOf(newSequence),
                now.toString()
        );

        // Step 4: Create and persist the audit event
        AuditEventEntity event = new AuditEventEntity();
        event.setTenantId(tenantId);
        event.setEventType(request.eventType());
        event.setActorId(request.actorId());
        event.setActorType(request.actorType());
        event.setSubjectRef(request.subjectRef());
        event.setResourceType(request.resourceType());
        event.setResourceId(request.resourceId());
        event.setAction(request.action());
        event.setOutcome(request.outcome());
        event.setPurposeOfUse(request.purposeOfUse());
        event.setFacilityId(request.facilityId());
        event.setCorrelationId(request.correlationId());
        event.setDetail(serializeDetail(request.detail()));
        event.setPreviousHash(previousHash);
        event.setEntryHash(entryHash);
        event.setSequenceNumber(newSequence);
        event.setCreatedAt(now);

        AuditEventEntity saved = eventRepository.save(event);

        // Step 5: Update the chain head
        chainHead.setCurrentHash(entryHash);
        chainHead.setSequenceNumber(newSequence);
        chainHead.setUpdatedAt(now);
        chainHeadRepository.save(chainHead);

        log.info("Appended audit event: tenant={}, seq={}, type={}, actor={}, hash={}",
                tenantId, newSequence, request.eventType(), request.actorId(), entryHash);

        return saved;
    }

    /**
     * Verify the hash chain integrity for a given tenant.
     * Walks forward from sequence 1, recomputing each hash and comparing
     * it to the stored entry_hash.
     *
     * @param tenantId the tenant whose chain to verify
     * @return verification result
     */
    @Transactional(readOnly = true)
    public ChainVerificationResponse verifyChain(UUID tenantId) {
        Optional<AuditChainHeadEntity> headOpt = chainHeadRepository.findByTenantId(tenantId);
        if (headOpt.isEmpty()) {
            return ChainVerificationResponse.empty(tenantId);
        }

        AuditChainHeadEntity head = headOpt.get();
        long totalEvents = head.getSequenceNumber();

        if (totalEvents == 0) {
            return ChainVerificationResponse.empty(tenantId);
        }

        // Walk the chain forward in batches
        long batchSize = 1000;
        long verified = 0;
        long legacyTimestampsRecovered = 0;
        String expectedPreviousHash = GENESIS_HASH;

        for (long start = 1; start <= totalEvents; start += batchSize) {
            long end = Math.min(start + batchSize - 1, totalEvents);
            List<AuditEventEntity> batch = eventRepository
                    .findByTenantIdAndSequenceNumberBetween(tenantId, start, end);

            for (AuditEventEntity event : batch) {
                // Verify the previous hash linkage
                if (!expectedPreviousHash.equals(event.getPreviousHash())) {
                    return ChainVerificationResponse.failure(
                            tenantId, verified, event.getSequenceNumber(),
                            expectedPreviousHash, event.getPreviousHash()
                    );
                }

                // Recompute the hash and verify
                String recomputedHash = computeHash(
                        event.getPreviousHash(),
                        event.getTenantId().toString(),
                        event.getEventType(),
                        event.getActorId(),
                        event.getAction(),
                        event.getOutcome(),
                        String.valueOf(event.getSequenceNumber()),
                        event.getCreatedAt().toString()
                );

                if (!recomputedHash.equals(event.getEntryHash())
                        && !matchesLegacyPrePersistenceTimestamp(event)) {
                    return ChainVerificationResponse.failure(
                            tenantId, verified, event.getSequenceNumber(),
                            recomputedHash, event.getEntryHash()
                    );
                }
                if (!recomputedHash.equals(event.getEntryHash())) {
                    legacyTimestampsRecovered++;
                }

                expectedPreviousHash = event.getEntryHash();
                verified++;
            }
        }

        // Final check: the last hash should match the chain head
        if (!expectedPreviousHash.equals(head.getCurrentHash())) {
            return ChainVerificationResponse.failure(
                    tenantId, verified, totalEvents,
                    expectedPreviousHash, head.getCurrentHash()
            );
        }

        if (legacyTimestampsRecovered > 0) {
            log.info("Verified audit chain for tenant={} with {} legacy sub-microsecond timestamps recovered",
                    tenantId, legacyTimestampsRecovered);
        }
        return ChainVerificationResponse.success(tenantId, verified);
    }

    /**
     * Before timestamps were normalized to PostgreSQL precision, an event was hashed with the
     * nanosecond value from {@link Instant#now()} and then persisted at microsecond precision.
     * The missing sub-microsecond component is recoverable without rewriting immutable events:
     * PostgreSQL's rounding window contains at most 1,000 possible original timestamps.
     */
    private boolean matchesLegacyPrePersistenceTimestamp(AuditEventEntity event) {
        Instant persistedTimestamp = event.getCreatedAt();
        for (int nanos = POSTGRES_TIMESTAMP_ROUNDING_MIN_NANOS;
             nanos <= POSTGRES_TIMESTAMP_ROUNDING_MAX_NANOS;
             nanos++) {
            if (nanos == 0) {
                continue;
            }
            String candidateHash = computeHash(
                    event.getPreviousHash(),
                    event.getTenantId().toString(),
                    event.getEventType(),
                    event.getActorId(),
                    event.getAction(),
                    event.getOutcome(),
                    String.valueOf(event.getSequenceNumber()),
                    persistedTimestamp.plusNanos(nanos).toString()
            );
            if (candidateHash.equals(event.getEntryHash())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compute SHA-256 hash from the concatenation of all input fields.
     * Hash formula: SHA-256(previousHash + tenantId + eventType + actorId + action + outcome + sequenceNumber + timestamp)
     */
    String computeHash(String previousHash, String tenantId, String eventType,
                       String actorId, String action, String outcome,
                       String sequenceNumber, String timestamp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = previousHash + tenantId + eventType + actorId
                    + action + outcome + sequenceNumber + timestamp;
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Create the initial chain head for a new tenant with the genesis hash.
     */
    private AuditChainHeadEntity createGenesisChainHead(UUID tenantId) {
        AuditChainHeadEntity head = new AuditChainHeadEntity();
        head.setTenantId(tenantId);
        head.setCurrentHash(GENESIS_HASH);
        head.setSequenceNumber(0L);
        head.setUpdatedAt(Instant.now());
        return chainHeadRepository.save(head);
    }

    /**
     * Serialize the detail map to JSON string. Returns null if detail is null.
     */
    private String serializeDetail(Object detail) {
        if (detail == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit event detail, storing as string: {}", e.getMessage());
            return detail.toString();
        }
    }
}
