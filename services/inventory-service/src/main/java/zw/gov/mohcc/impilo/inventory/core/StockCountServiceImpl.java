package zw.gov.mohcc.impilo.inventory.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.CountSessionStatus;
import zw.gov.mohcc.impilo.inventory.domain.LedgerEventType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CountLineEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CountSessionEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.OnHandEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.CountLineRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.CountSessionRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.OnHandRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stock count session management implementation.
 *
 * <p>Implements the full count lifecycle: session creation (with pre-populated
 * lines from on-hand), counting, submission, and approval/rejection. On approval,
 * variance adjustments are posted as COUNT_ADJUST ledger events to reconcile
 * the system on-hand with the physical count.</p>
 *
 * <p>State machine:
 * <pre>
 *   DRAFT -> IN_PROGRESS -> SUBMITTED -> APPROVED
 *                                     -> REJECTED -> IN_PROGRESS (recount)
 * </pre>
 */
@Service
public class StockCountServiceImpl implements StockCountService {

    private static final Logger log = LoggerFactory.getLogger(StockCountServiceImpl.class);

    private final CountSessionRepository sessionRepository;
    private final CountLineRepository lineRepository;
    private final OnHandRepository onHandRepository;
    private final LedgerService ledgerService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public StockCountServiceImpl(CountSessionRepository sessionRepository,
                                  CountLineRepository lineRepository,
                                  OnHandRepository onHandRepository,
                                  LedgerService ledgerService,
                                  EventOutboxRepository outboxRepository,
                                  ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.lineRepository = lineRepository;
        this.onHandRepository = onHandRepository;
        this.ledgerService = ledgerService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a count session in DRAFT status and populates count lines from
     * the current on-hand positions. Each on-hand row becomes a count line with
     * {@code qtyExpected} set to the current on-hand quantity.</p>
     */
    @Override
    @Transactional
    public CountSessionEntity createSession(UUID storeId, UUID binId) {
        TrustContext ctx = TrustContextHolder.require();

        CountSessionEntity session = new CountSessionEntity();
        session.setTenantId(ctx.tenantId());
        session.setFacilityId(ctx.facilityId());
        session.setStoreId(storeId);
        session.setBinId(binId);
        session.setStatus(CountSessionStatus.DRAFT);
        session.setCreatedBy(ctx.actorId());

        session = sessionRepository.save(session);
        UUID sessionId = session.getSessionId();

        // Populate count lines from current on-hand
        List<OnHandEntity> onHandRows;
        if (binId != null) {
            onHandRows = onHandRepository.findByFacilityIdAndStoreIdAndBinId(
                    ctx.facilityId(), storeId, binId);
        } else {
            onHandRows = onHandRepository.findByFacilityIdAndStoreId(
                    ctx.facilityId(), storeId);
        }

        int lineCount = 0;
        for (OnHandEntity onHand : onHandRows) {
            CountLineEntity line = new CountLineEntity();
            line.setSessionId(sessionId);
            line.setItemCode(onHand.getItemCode());
            line.setBatch(onHand.getBatch());
            line.setExpiry(onHand.getExpiry());
            line.setBinId(onHand.getBinId());
            line.setQtySystem(onHand.getQtyOnHand());
            lineRepository.save(line);
            lineCount++;
        }

        log.info("Count session created: sessionId={}, store={}, bin={}, lines={}",
                sessionId, storeId, binId, lineCount);

        publishOutboxEvent("COUNT_SESSION", sessionId.toString(),
                "COUNT_SESSION_CREATED", session, ctx.tenantId());

        return session;
    }

    @Override
    @Transactional
    public CountSessionEntity startCounting(UUID sessionId) {
        CountSessionEntity session = loadSession(sessionId);
        CountSessionStatus currentStatus = session.getStatus();

        if (currentStatus != CountSessionStatus.DRAFT && currentStatus != CountSessionStatus.REJECTED) {
            throw new IllegalStateException(
                    "Cannot start counting: session " + sessionId + " is in status " + currentStatus
                            + " (expected DRAFT or REJECTED)");
        }

        session.setStatus(CountSessionStatus.IN_PROGRESS);
        session = sessionRepository.save(session);

        TrustContext ctx = TrustContextHolder.require();
        publishOutboxEvent("COUNT_SESSION", sessionId.toString(),
                "COUNT_SESSION_STARTED", session, ctx.tenantId());

        log.info("Count session started: sessionId={}, previousStatus={}", sessionId, currentStatus);
        return session;
    }

    @Override
    @Transactional
    public CountLineEntity updateLine(UUID sessionId, UUID lineId, int qtyCounted, String barcode) {
        CountSessionEntity session = loadSession(sessionId);

        if (session.getStatus() != CountSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Cannot update line: session " + sessionId + " is in status " + session.getStatus()
                            + " (expected IN_PROGRESS)");
        }

        CountLineEntity line = lineRepository.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("Count line not found: " + lineId));

        if (!line.getSessionId().equals(sessionId)) {
            throw new IllegalArgumentException(
                    "Count line " + lineId + " does not belong to session " + sessionId);
        }

        line.setQtyCounted(qtyCounted);
        line.setBarcodeScanned(barcode);
        line.setVariance(qtyCounted - line.getQtySystem());
        line.setCountedAt(OffsetDateTime.now());

        line = lineRepository.save(line);

        log.debug("Count line updated: sessionId={}, lineId={}, expected={}, counted={}, variance={}",
                sessionId, lineId, line.getQtySystem(), qtyCounted, line.getVariance());

        return line;
    }

    @Override
    @Transactional
    public CountSessionEntity submitCount(UUID sessionId) {
        CountSessionEntity session = loadSession(sessionId);

        if (session.getStatus() != CountSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Cannot submit count: session " + sessionId + " is in status " + session.getStatus()
                            + " (expected IN_PROGRESS)");
        }

        // Finalize all variance calculations
        List<CountLineEntity> lines = lineRepository.findBySessionId(sessionId);
        for (CountLineEntity line : lines) {
            if (line.getQtyCounted() != null) {
                int variance = line.getQtyCounted() - line.getQtySystem();
                line.setVariance(variance);
            } else {
                // Line not counted — treat as zero counted
                line.setQtyCounted(0);
                line.setVariance(-line.getQtySystem());
            }
            lineRepository.save(line);
        }

        session.setStatus(CountSessionStatus.SUBMITTED);
        session = sessionRepository.save(session);

        TrustContext ctx = TrustContextHolder.require();
        publishOutboxEvent("COUNT_SESSION", sessionId.toString(),
                "COUNT_SESSION_SUBMITTED", session, ctx.tenantId());

        log.info("Count session submitted: sessionId={}, totalLines={}", sessionId, lines.size());
        return session;
    }

    /**
     * {@inheritDoc}
     *
     * <p>For each count line with a non-zero variance, a COUNT_ADJUST ledger
     * event is posted with {@code qtyDelta = variance}. This brings the on-hand
     * projection in line with the physical count.</p>
     */
    @Override
    @Transactional
    public CountSessionEntity approveCount(UUID sessionId, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        CountSessionEntity session = loadSession(sessionId);

        if (session.getStatus() != CountSessionStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Cannot approve count: session " + sessionId + " is in status " + session.getStatus()
                            + " (expected SUBMITTED)");
        }

        // Post COUNT_ADJUST ledger events for all non-zero variances
        List<CountLineEntity> lines = lineRepository.findBySessionId(sessionId);
        int adjustmentCount = 0;

        for (CountLineEntity line : lines) {
            int variance = line.getVariance() != null ? line.getVariance() : 0;
            if (variance != 0) {
                String idempotencyKey = "COUNT:" + sessionId + ":LINE:" + line.getLineId() + ":ADJUST";

                LedgerService.LedgerEventData eventData = new LedgerService.LedgerEventData(
                        session.getFacilityId(),
                        session.getStoreId(),
                        line.getBinId(),
                        line.getItemCode(),
                        line.getBatch(),
                        line.getExpiry(),
                        variance,
                        null, // uom inherited from item
                        LedgerEventType.COUNT_ADJUST,
                        "Stock count adjustment - session " + sessionId,
                        "COUNT_SESSION",
                        sessionId.toString(),
                        idempotencyKey
                );

                ledgerService.postEvent(eventData);
                adjustmentCount++;

                log.debug("Count adjustment posted: sessionId={}, item={}, batch={}, variance={}",
                        sessionId, line.getItemCode(), line.getBatch(), variance);
            }
        }

        // Update session status
        session.setStatus(CountSessionStatus.APPROVED);
        session.setApprovedBy(ctx.actorId());
        session.setApprovedAt(OffsetDateTime.now());
        session.setNotes(notes);
        session = sessionRepository.save(session);

        publishOutboxEvent("COUNT_SESSION", sessionId.toString(),
                "COUNT_SESSION_APPROVED",
                Map.of("sessionId", sessionId.toString(), "adjustments", adjustmentCount),
                ctx.tenantId());

        log.info("Count session approved: sessionId={}, adjustments={}", sessionId, adjustmentCount);
        return session;
    }

    @Override
    @Transactional
    public CountSessionEntity rejectCount(UUID sessionId, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        CountSessionEntity session = loadSession(sessionId);

        if (session.getStatus() != CountSessionStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Cannot reject count: session " + sessionId + " is in status " + session.getStatus()
                            + " (expected SUBMITTED)");
        }

        session.setStatus(CountSessionStatus.REJECTED);
        session.setNotes(notes);
        session = sessionRepository.save(session);

        publishOutboxEvent("COUNT_SESSION", sessionId.toString(),
                "COUNT_SESSION_REJECTED", session, ctx.tenantId());

        log.info("Count session rejected: sessionId={}, notes={}", sessionId, notes);
        return session;
    }

    @Override
    public CountSessionEntity getSession(UUID sessionId) {
        return loadSession(sessionId);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private CountSessionEntity loadSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Count session not found: " + sessionId));
    }

    private void publishOutboxEvent(String aggregateType, String aggregateId,
                                     String eventType, Object payload, UUID tenantId) {
        try {
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType(aggregateType);
            outbox.setAggregateId(aggregateId);
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(tenantId);
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write outbox event: type={}, aggregateId={}", eventType, aggregateId, e);
        }
    }
}
