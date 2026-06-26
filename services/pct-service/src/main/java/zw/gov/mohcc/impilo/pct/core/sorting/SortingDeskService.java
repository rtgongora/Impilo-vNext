package zw.gov.mohcc.impilo.pct.core.sorting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.SortingRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.SortingRecordRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The Sorting Desk (decision D5): the explicit pre-queue step that assigns a visit-type to a journey.
 *
 * <p>Records the sort decision (visit-type, context, fast-track), feeding the Cadre Engine (C9) and routing.
 * It does <strong>not</strong> drive a journey state change (sorting sits between ARRIVED and TRIAGE/QUEUE);
 * the journey state machine remains the SoR for clinical sub-state.</p>
 *
 * <p>Authorization is enforced upstream at Envoy ext_authz (policy {@code TRIAGE-RECORD} family / track P).</p>
 */
@Service
public class SortingDeskService {

    private static final Logger log = LoggerFactory.getLogger(SortingDeskService.class);

    private final SortingRecordRepository sortingRepository;
    private final JourneyRepository journeyRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SortingDeskService(SortingRecordRepository sortingRepository,
                              JourneyRepository journeyRepository,
                              EventOutboxRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.sortingRepository = sortingRepository;
        this.journeyRepository = journeyRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Sort a journey: resolve the visit-type within the given context and persist the decision.
     *
     * @param journeyId          the journey being sorted
     * @param context            one of the six work contexts
     * @param requestedVisitType the visit-type code (unknown → emergency assessment fallback)
     * @param arrivalReason      free-text reason; may be {@code null}
     * @return the persisted sorting record
     */
    @Transactional
    public SortingRecordEntity sort(String journeyId, String context, String requestedVisitType, String arrivalReason) {
        TrustContext ctx = TrustContextHolder.require();

        JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                .orElseThrow(() -> new IllegalArgumentException("Journey not found: " + journeyId));

        VisitTypeCatalog.VisitType vt = VisitTypeCatalog.resolve(context, requestedVisitType);
        String normContext = CadreContext(context);

        SortingRecordEntity row = new SortingRecordEntity();
        row.setTenantId(ctx.tenantId());
        row.setJourneyId(journey.getJourneyId());
        row.setVisitType(vt.code());
        row.setContext(normContext);
        row.setArrivalReason(arrivalReason);
        row.setFastTrack(vt.fastTrackDefault());
        row.setSortedBy(ctx.actorId());
        row.setCorrelationId(ctx.correlationId());
        row = sortingRepository.save(row);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("journeyId", journey.getJourneyId());
        payload.put("patientCpid", journey.getPatientCpid());
        payload.put("visitType", vt.code());
        payload.put("context", normContext);
        payload.put("fastTrack", vt.fastTrackDefault());
        payload.put("sortedBy", ctx.actorId());
        writeOutbox("SORTING", journey.getJourneyId(), "JOURNEY_SORTED", toJson(payload), ctx);

        log.info("pct.sorting.sorted journey={} visitType={} context={} fastTrack={} by={} correlationId={}",
                journey.getJourneyId(), vt.code(), normContext, vt.fastTrackDefault(), ctx.actorId(), ctx.correlationId());

        return row;
    }

    /** The latest sort decision for a journey, if any (used by the cockpit to supply visitType to the Cadre Engine). */
    @Transactional(readOnly = true)
    public Optional<SortingRecordEntity> latestFor(String journeyId) {
        return sortingRepository.findFirstByTenantIdAndJourneyIdOrderByCreatedAtDesc(
                TrustContextHolder.require().tenantId(), journeyId);
    }

    private static String CadreContext(String raw) {
        return raw == null || raw.isBlank() ? "OUTPATIENT" : raw.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private void writeOutbox(String aggregateType, String aggregateId, String eventType,
                             String payloadJson, TrustContext ctx) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payloadJson);
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise sorting payload: {}", e.getMessage());
            return "{}";
        }
    }
}
