package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAuthorizationLinkEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderAuthorizationLinkRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes the authoritative HID ↔ Provider ID binding record (D-P1) alongside
 * every bind/rebind. Joins the caller's transaction (claim / recovery /
 * adjudicated rebind) so the denormalized {@code claimed_health_id} pointer and
 * the link row can never diverge on commit.
 */
@Service
public class ProviderAuthorizationLinkService {

    private static final Logger log = LoggerFactory.getLogger(ProviderAuthorizationLinkService.class);

    private final ProviderAuthorizationLinkRepository linkRepository;

    public ProviderAuthorizationLinkService(ProviderAuthorizationLinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    /**
     * Record a new ACTIVE binding, superseding any previous ACTIVE link for the
     * provider (rebind history is preserved, never overwritten).
     *
     * @param evidenceRef      non-secret pointer to what proved the binding
     *                         (e.g. {@code claim-token:42}, {@code recovery-token:7},
     *                         an adjudication case id)
     * @param assuranceOutcome Identity Journey Doctrine §4 outcome (e.g. RECORD_LINKED)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProviderAuthorizationLinkEntity recordBinding(UUID tenantId,
                                                         ProviderEntity provider,
                                                         UUID impiloHealthId,
                                                         String linkType,
                                                         String evidenceRef,
                                                         String assuranceOutcome,
                                                         String proofingChannel,
                                                         String actorRef) {
        if (impiloHealthId == null) {
            throw new IllegalArgumentException("impiloHealthId is required for an authorization link");
        }
        int superseded = linkRepository.supersedeActiveForProvider(tenantId, provider.getId(), Instant.now());
        if (superseded > 0) {
            log.info("superseded {} authorization link(s) for providerPublicId={} ahead of {} rebind",
                    superseded, provider.getProviderPublicId(), linkType);
        }
        ProviderAuthorizationLinkEntity link = new ProviderAuthorizationLinkEntity();
        link.setTenantId(tenantId);
        link.setProviderId(provider.getId());
        link.setProviderPublicId(provider.getProviderPublicId());
        link.setImpiloHealthId(impiloHealthId);
        link.setLinkType(linkType);
        link.setStatus(ProviderAuthorizationLinkEntity.STATUS_ACTIVE);
        link.setEvidenceRef(evidenceRef);
        link.setAssuranceOutcome(assuranceOutcome);
        link.setProofingChannel(proofingChannel);
        link.setActorRef(actorRef);
        return linkRepository.save(link);
    }
}
