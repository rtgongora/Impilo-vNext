package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.integration.TusoClient;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderPracticeContextEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderPracticeContextRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service managing provider practice context authorizations.
 * Tracks where and how a provider is authorized to practice.
 */
@Service
public class PracticeContextService {

    private static final Logger log = LoggerFactory.getLogger(PracticeContextService.class);

    private final ProviderPracticeContextRepository contextRepository;
    private final ProviderRepository providerRepository;
    private final TusoClient tusoClient;
    private final EventOutboxRepository outboxRepository;

    public PracticeContextService(
            ProviderPracticeContextRepository contextRepository,
            ProviderRepository providerRepository,
            TusoClient tusoClient,
            EventOutboxRepository outboxRepository) {
        this.contextRepository = contextRepository;
        this.providerRepository = providerRepository;
        this.tusoClient = tusoClient;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Request practice context authorization.
     */
    @Transactional
    public ProviderPracticeContextEntity requestContext(
            Long providerId,
            String contextType,
            Long linkedFacilityId,
            String serviceScopeSummary,
            LocalDate startDate,
            LocalDate endDate,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Requesting practice context: providerId={}, type={}, actor={}",
                providerId, contextType, ctx.actorId());

        ProviderEntity provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));

        // Validate provider is active
        if (!"ACTIVE".equals(provider.getStatus())) {
            throw new IllegalStateException("Provider is not active");
        }

        // Validate facility if provided
        if (linkedFacilityId != null && !tusoClient.validateFacilityExists(linkedFacilityId)) {
            throw new IllegalArgumentException("Facility not found: " + linkedFacilityId);
        }

        // Check for duplicate active context
        List<ProviderPracticeContextEntity> existing = contextRepository
                .findByProviderIdAndContextType(providerId, contextType);
        boolean hasActive = existing.stream().anyMatch(c -> "ACTIVE".equals(c.getStatus()));
        if (hasActive) {
            log.warn("Provider already has active context for type: {}", contextType);
        }

        ProviderPracticeContextEntity context = new ProviderPracticeContextEntity();
        context.setProvider(provider);
        context.setTenantId(ctx.tenantId());
        context.setContextType(contextType);
        context.setLinkedFacilityId(linkedFacilityId);
        context.setServiceScopeSummary(serviceScopeSummary);
        context.setStartDate(startDate);
        context.setEndDate(endDate);
        context.setStatus("REQUESTED");
        context.setNotes(notes);
        context.setVersion(1);

        context = contextRepository.save(context);

        log.info("Practice context requested: id={}, providerId={}, type={}",
                context.getId(), providerId, contextType);
        publishEvent("PRACTICE_CONTEXT", context.getId().toString(),
                "varapi.practice.context.requested",
                String.format("{\"contextId\":%d,\"providerId\":%d,\"type\":\"%s\"}",
                        context.getId(), providerId, contextType));

        return context;
    }

    /**
     * Approve practice context authorization.
     */
    @Transactional
    public ProviderPracticeContextEntity approveContext(
            Long contextId,
            String authorisationBasis,
            LocalDate decisionDate,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Approving practice context: id={}, actor={}", contextId, ctx.actorId());

        ProviderPracticeContextEntity context = contextRepository.findById(contextId)
                .orElseThrow(() -> new IllegalArgumentException("Context not found: " + contextId));

        if (!"REQUESTED".equals(context.getStatus()) && !"UNDER_REVIEW".equals(context.getStatus())) {
            throw new IllegalStateException("Can only approve requested or under-review contexts");
        }

        context.setStatus("ACTIVE");
        context.setAuthorisationBasis(authorisationBasis);
        context.setApprovedBy(ctx.actorId());
        context.setDecisionDate(decisionDate != null ? decisionDate : LocalDate.now());
        context.setNotes(notes != null ? notes : context.getNotes());
        context.setVersion(context.getVersion() + 1);

        context = contextRepository.save(context);

        log.info("Practice context approved: id={}", contextId);
        publishEvent("PRACTICE_CONTEXT", context.getId().toString(),
                "varapi.practice.context.approved",
                String.format("{\"contextId\":%d,\"providerId\":%d,\"type\":\"%s\"}",
                        contextId, context.getProvider().getId(), context.getContextType()));

        return context;
    }

    /**
     * Restrict practice context.
     */
    @Transactional
    public ProviderPracticeContextEntity restrictContext(Long contextId, String restrictionReason, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Restricting practice context: id={}", contextId);

        ProviderPracticeContextEntity context = contextRepository.findById(contextId)
                .orElseThrow(() -> new IllegalArgumentException("Context not found: " + contextId));

        context.setStatus("RESTRICTED");
        context.setNotes(notes != null ? notes : context.getNotes());
        context.setVersion(context.getVersion() + 1);

        context = contextRepository.save(context);

        log.info("Practice context restricted: id={}", contextId);
        publishEvent("PRACTICE_CONTEXT", context.getId().toString(),
                "varapi.practice.context.restricted",
                String.format("{\"contextId\":%d}", contextId));

        return context;
    }

    /**
     * Revoke practice context.
     */
    @Transactional
    public ProviderPracticeContextEntity revokeContext(Long contextId, LocalDate endDate, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Revoking practice context: id={}", contextId);

        ProviderPracticeContextEntity context = contextRepository.findById(contextId)
                .orElseThrow(() -> new IllegalArgumentException("Context not found: " + contextId));

        context.setStatus("REVOKED");
        context.setEndDate(endDate != null ? endDate : LocalDate.now());
        context.setNotes(reason);
        context.setVersion(context.getVersion() + 1);

        context = contextRepository.save(context);

        log.info("Practice context revoked: id={}", contextId);
        publishEvent("PRACTICE_CONTEXT", context.getId().toString(),
                "varapi.practice.context.revoked",
                String.format("{\"contextId\":%d}", contextId));

        return context;
    }

    /**
     * Get context by ID.
     */
    @Transactional(readOnly = true)
    public ProviderPracticeContextEntity getContext(Long contextId) {
        return contextRepository.findById(contextId)
                .orElseThrow(() -> new IllegalArgumentException("Context not found: " + contextId));
    }

    /**
     * Get all contexts for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderPracticeContextEntity> getContextsByProvider(Long providerId) {
        return contextRepository.findByProviderId(providerId);
    }

    /**
     * Get active contexts for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderPracticeContextEntity> getActiveContexts(Long providerId) {
        return contextRepository.findByProviderIdAndStatus(providerId, "ACTIVE");
    }

    /**
     * Get active contexts for a facility.
     */
    @Transactional(readOnly = true)
    public List<ProviderPracticeContextEntity> getActiveContextsByFacility(Long facilityId) {
        return contextRepository.findByLinkedFacilityIdAndStatus(facilityId, "ACTIVE");
    }

    /**
     * Check if provider is authorized for a context type.
     */
    @Transactional(readOnly = true)
    public boolean isAuthorizedForContext(Long providerId, String contextType) {
        List<ProviderPracticeContextEntity> contexts = contextRepository
                .findByProviderIdAndContextType(providerId, contextType);
        return contexts.stream().anyMatch(c -> "ACTIVE".equals(c.getStatus()));
    }

    private void publishEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}