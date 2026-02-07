package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CpdCycleEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CpdEvidenceEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CpdEventEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.DocumentEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CpdCycleRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CpdEvidenceRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CpdEventRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Continuing Professional Development (CPD/CME) tracking service.
 *
 * Manages CPD cycles, events (learning activities), and evidence for
 * healthcare providers. Councils define required points per cycle;
 * this service tracks earned points and verifies completion.
 *
 * CPD cycle lifecycle:
 *   IN_PROGRESS -> COMPLETE (when required points met and cycle end date reached)
 *   IN_PROGRESS -> EXPIRED (when end date passed without meeting requirements)
 */
@Service
public class CpdService {

    private static final Logger log = LoggerFactory.getLogger(CpdService.class);

    private final CpdCycleRepository cycleRepository;
    private final CpdEventRepository eventRepository;
    private final CpdEvidenceRepository evidenceRepository;
    private final ProviderRepository providerRepository;
    private final EventOutboxRepository outboxRepository;

    public CpdService(CpdCycleRepository cycleRepository,
                      CpdEventRepository eventRepository,
                      CpdEvidenceRepository evidenceRepository,
                      ProviderRepository providerRepository,
                      EventOutboxRepository outboxRepository) {
        this.cycleRepository = cycleRepository;
        this.eventRepository = eventRepository;
        this.evidenceRepository = evidenceRepository;
        this.providerRepository = providerRepository;
        this.outboxRepository = outboxRepository;
    }

    // ---- Inner DTOs ----

    public record CreateCpdCycleRequest(
            Long councilId,
            String cycleName,
            LocalDate startDate,
            LocalDate endDate,
            int requiredPoints
    ) {}

    public record RecordCpdEventRequest(
            String eventType,
            String title,
            String description,
            int pointsAwarded,
            LocalDate eventDate,
            String externalRef
    ) {}

    public record CpdSummary(
            CpdCycleEntity currentCycle,
            List<CpdEventEntity> events,
            int earnedPoints,
            int requiredPoints,
            boolean requirementMet
    ) {}

    // ---- Public API ----

    /**
     * Create a new CPD cycle for a provider.
     *
     * @param providerPublicId the provider to create the cycle for
     * @param request          the cycle definition
     * @return the created CPD cycle
     * @throws IllegalArgumentException if the provider does not exist
     */
    @Transactional
    public CpdCycleEntity createCycle(String providerPublicId, CreateCpdCycleRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating CPD cycle: providerPublicId={}, cycleName={}, actor={}",
                providerPublicId, request.cycleName(), ctx.actorId());

        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found: " + providerPublicId));

        CpdCycleEntity cycle = new CpdCycleEntity();
        cycle.setProvider(provider);
        cycle.setTenantId(ctx.tenantId());
        cycle.setCycleName(request.cycleName());
        cycle.setStartDate(request.startDate());
        cycle.setEndDate(request.endDate());
        cycle.setRequiredPoints(request.requiredPoints());
        cycle.setEarnedPoints(0);
        cycle.setStatus("IN_PROGRESS");

        // Resolve council reference if provided
        if (request.councilId() != null) {
            CouncilEntity councilRef = new CouncilEntity();
            councilRef.setId(request.councilId());
            cycle.setCouncil(councilRef);
        }

        cycle = cycleRepository.save(cycle);
        log.info("CPD cycle created: cycleId={}, providerPublicId={}", cycle.getId(), providerPublicId);

        publishEvent("CPD_CYCLE", providerPublicId,
                "varapi.cpd.cycle.created",
                String.format("{\"cycleId\":%d,\"providerPublicId\":\"%s\"," +
                                "\"cycleName\":\"%s\",\"requiredPoints\":%d}",
                        cycle.getId(), providerPublicId,
                        request.cycleName(), request.requiredPoints()));

        return cycle;
    }

    /**
     * Record a CPD learning event within an existing cycle.
     *
     * Updates the cycle's earned_points total. If earned points meet or
     * exceed required points, the cycle status may be updated to COMPLETE.
     *
     * @param cycleId the CPD cycle to record the event in
     * @param request the learning event details
     * @return the created CPD event
     * @throws IllegalArgumentException if the cycle does not exist
     * @throws IllegalStateException    if the cycle is not IN_PROGRESS
     */
    @Transactional
    public CpdEventEntity recordEvent(Long cycleId, RecordCpdEventRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Recording CPD event: cycleId={}, title={}, points={}, actor={}",
                cycleId, request.title(), request.pointsAwarded(), ctx.actorId());

        CpdCycleEntity cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new IllegalArgumentException("CPD cycle not found: " + cycleId));

        if (!cycle.isInProgress()) {
            throw new IllegalStateException(
                    "CPD cycle is not IN_PROGRESS: current=" + cycle.getStatus());
        }

        CpdEventEntity event = new CpdEventEntity();
        event.setCycle(cycle);
        event.setProvider(cycle.getProvider());
        event.setTenantId(ctx.tenantId());
        event.setEventType(request.eventType());
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setPointsAwarded(request.pointsAwarded());
        event.setEventDate(request.eventDate());
        event.setExternalRef(request.externalRef());
        event.setVerified(false);
        event = eventRepository.save(event);

        // Update earned points on the cycle
        int newTotal = cycle.getEarnedPoints() + request.pointsAwarded();
        cycle.setEarnedPoints(newTotal);

        // Check if requirement is now met
        if (cycle.isPointsRequirementMet()) {
            log.info("CPD cycle requirement met: cycleId={}, earned={}, required={}",
                    cycleId, newTotal, cycle.getRequiredPoints());
        }

        cycleRepository.save(cycle);

        String providerPublicId = cycle.getProvider().getProviderPublicId();
        log.info("CPD event recorded: eventId={}, cycleId={}, totalPoints={}",
                event.getId(), cycleId, newTotal);

        publishEvent("CPD_EVENT", providerPublicId,
                "varapi.cpd.event.recorded",
                String.format("{\"eventId\":%d,\"cycleId\":%d,\"providerPublicId\":\"%s\"," +
                                "\"pointsAwarded\":%d,\"totalEarned\":%d}",
                        event.getId(), cycleId, providerPublicId,
                        request.pointsAwarded(), newTotal));

        return event;
    }

    /**
     * Upload evidence for a CPD learning event.
     *
     * Links a document to a CPD event as supporting evidence.
     *
     * @param eventId      the CPD event to attach evidence to
     * @param documentId   the document entity ID
     * @param evidenceType the type of evidence (e.g., CERTIFICATE, ATTENDANCE)
     * @param notes        optional notes about the evidence
     * @return the created evidence entity
     * @throws IllegalArgumentException if the event does not exist
     */
    @Transactional
    public CpdEvidenceEntity uploadEvidence(Long eventId, Long documentId,
                                             String evidenceType, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Uploading CPD evidence: eventId={}, documentId={}, type={}, actor={}",
                eventId, documentId, evidenceType, ctx.actorId());

        CpdEventEntity event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("CPD event not found: " + eventId));

        CpdEvidenceEntity evidence = new CpdEvidenceEntity();
        evidence.setEvent(event);
        evidence.setTenantId(ctx.tenantId());
        evidence.setEvidenceType(evidenceType);
        evidence.setNotes(notes);

        // Link document reference
        if (documentId != null) {
            DocumentEntity docRef = new DocumentEntity();
            docRef.setId(documentId);
            evidence.setDocument(docRef);
        }

        evidence = evidenceRepository.save(evidence);
        log.info("CPD evidence uploaded: evidenceId={}, eventId={}", evidence.getId(), eventId);

        return evidence;
    }

    /**
     * Get a summary of a provider's current CPD status.
     *
     * Returns the most recent IN_PROGRESS cycle along with all events
     * and a points progress breakdown.
     *
     * @param providerPublicId the provider to summarize
     * @return the CPD summary, or a summary with null cycle if no active cycle exists
     * @throws IllegalArgumentException if the provider does not exist
     */
    @Transactional(readOnly = true)
    public CpdSummary getCpdSummary(String providerPublicId) {
        TrustContextHolder.require();
        log.debug("Fetching CPD summary: providerPublicId={}", providerPublicId);

        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found: " + providerPublicId));

        // Find the current in-progress cycle
        List<CpdCycleEntity> activeCycles =
                cycleRepository.findByProviderIdAndStatus(provider.getId(), "IN_PROGRESS");

        if (activeCycles.isEmpty()) {
            return new CpdSummary(null, List.of(), 0, 0, false);
        }

        // Use the most recent active cycle
        CpdCycleEntity currentCycle = activeCycles.get(0);
        List<CpdEventEntity> events = eventRepository.findByCycleId(currentCycle.getId());

        return new CpdSummary(
                currentCycle,
                events,
                currentCycle.getEarnedPoints(),
                currentCycle.getRequiredPoints(),
                currentCycle.isPointsRequirementMet()
        );
    }

    /**
     * Mark a CPD event as verified by a council or supervisor.
     *
     * @param eventId the CPD event to verify
     * @return the verified event entity
     * @throws IllegalArgumentException if the event does not exist
     */
    @Transactional
    public CpdEventEntity verifyCpdEvent(Long eventId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Verifying CPD event: eventId={}, actor={}", eventId, ctx.actorId());

        CpdEventEntity event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("CPD event not found: " + eventId));

        event.setVerified(true);
        event.setVerifiedBy(ctx.actorId());
        event.setVerifiedAt(Instant.now());
        event = eventRepository.save(event);

        log.info("CPD event verified: eventId={}", eventId);

        return event;
    }

    // ---- Private helpers ----

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}
