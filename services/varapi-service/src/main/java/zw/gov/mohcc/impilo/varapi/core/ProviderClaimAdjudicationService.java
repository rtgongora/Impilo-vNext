package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.enums.ProviderRegistryStatus;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * WS-F provider-claim CONFLICT producer (IATG Journey F). When a claim is
 * attempted onto a Health ID that already owns a distinct provider profile, this
 * marks the existing profile's registry status CONFLICT and starts a provider
 * adjudication workflow (config-gated {@code impilo.varapi.adjudication.enabled},
 * default off).
 *
 * <p>Runs in its OWN transaction (REQUIRES_NEW) so the durable CONFLICT + workflow
 * survive the caller's subsequent rejection of the claim (the claim itself must
 * still fail — one person, one profile). Best-effort: never throws into the caller.</p>
 *
 * <p>The resolve write-back (what an APPROVED/DENIED decision does to the provider)
 * is deliberately NOT automated here: for a self-claim conflict the resolution
 * (merge / reject / reissue) is a doctrine decision, not a mechanical mapping. The
 * decision is recorded append-only in workforce-governance; wiring an automatic
 * varapi write-back is a documented follow-up.</p>
 */
@Service
public class ProviderClaimAdjudicationService {

    private static final Logger log = LoggerFactory.getLogger(ProviderClaimAdjudicationService.class);

    private final ProviderRepository providerRepository;
    private final EventOutboxRepository outboxRepository;
    private final ProviderWorkflowServiceClient workflowClient;

    public ProviderClaimAdjudicationService(ProviderRepository providerRepository,
                                            EventOutboxRepository outboxRepository,
                                            ProviderWorkflowServiceClient workflowClient) {
        this.providerRepository = providerRepository;
        this.outboxRepository = outboxRepository;
        this.workflowClient = workflowClient;
    }

    public boolean isEnabled() {
        return workflowClient.isEnabled();
    }

    /**
     * Escalate a provider-claim conflict on the existing profile. No-op (and no
     * behaviour change) when adjudication is disabled.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void escalate(UUID tenantId, Long existingProviderId, UUID claimantHealthId,
                         String actorId, String correlationId) {
        if (!workflowClient.isEnabled()) {
            return;
        }
        try {
            Optional<ProviderEntity> found = providerRepository.findByIdAndTenantId(existingProviderId, tenantId);
            if (found.isEmpty()) {
                return;
            }
            ProviderEntity existing = found.get();
            existing.setRegistryStatus(ProviderRegistryStatus.CONFLICT.name());
            existing.setUpdatedBy(actorId);
            existing = providerRepository.save(existing);

            Map<String, Object> context = new LinkedHashMap<>();
            context.put("providerPublicId", existing.getProviderPublicId());
            context.put("claimantHealthId", claimantHealthId.toString());
            context.put("tenantId", tenantId.toString());
            workflowClient.startAdjudication(tenantId, existing.getProviderPublicId(), context,
                    "varapi-provider-claim", correlationId);

            publishEvent("PROVIDER", existing.getProviderPublicId(),
                    "varapi.provider.claim.conflicted",
                    String.format("{\"providerPublicId\":\"%s\",\"claimantHealthId\":\"%s\",\"registryStatus\":\"CONFLICT\"}",
                            existing.getProviderPublicId(), claimantHealthId));
            log.info("provider-claim conflict escalated: providerPublicId={} status=CONFLICT",
                    existing.getProviderPublicId());
        } catch (Exception e) {
            log.warn("provider-claim conflict escalation failed ({}: {}) — claim still rejected",
                    e.getClass().getSimpleName(), e.getMessage());
        }
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
