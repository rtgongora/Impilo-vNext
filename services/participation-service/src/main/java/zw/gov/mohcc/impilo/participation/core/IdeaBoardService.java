package zw.gov.mohcc.impilo.participation.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.participation.domain.ModerationState;
import zw.gov.mohcc.impilo.participation.persistence.entity.ContributionEntity;
import zw.gov.mohcc.impilo.participation.persistence.entity.ContributionSupportEntity;
import zw.gov.mohcc.impilo.participation.persistence.repository.ContributionRepository;
import zw.gov.mohcc.impilo.participation.persistence.repository.ContributionSupportRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The public idea board: lists only APPROVED (moderated) contributions and lets a supporter
 * back an idea at most once. Support keys are opaque (anonymous supporter key or authenticated
 * actor ref); no PII is stored.
 */
@Service
public class IdeaBoardService {

    private final ContributionRepository contributionRepository;
    private final ContributionSupportRepository supportRepository;
    private final ContributionService contributionService;

    public IdeaBoardService(ContributionRepository contributionRepository,
                            ContributionSupportRepository supportRepository,
                            ContributionService contributionService) {
        this.contributionRepository = contributionRepository;
        this.supportRepository = supportRepository;
        this.contributionService = contributionService;
    }

    /** The public board — only moderation-APPROVED contributions, most-supported first. */
    @Transactional(readOnly = true)
    public List<ContributionEntity> board(UUID tenantId) {
        return contributionRepository
                .findByTenantIdAndModerationStateOrderBySupportCountDescCreatedAtDesc(
                        tenantId, ModerationState.APPROVED);
    }

    /**
     * Record a "support this idea" from an opaque supporter. Idempotent per supporter:
     * a repeat support is a no-op that returns the current count. Only APPROVED, board-visible
     * contributions can be supported.
     */
    @Transactional
    public ContributionEntity support(UUID tenantId, UUID contributionId, String supporterKey) {
        if (supporterKey == null || supporterKey.isBlank()) {
            throw new IllegalArgumentException("A supporter key is required.");
        }
        ContributionEntity c = contributionRepository.findByIdAndTenantId(contributionId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Contribution not found: " + contributionId));
        if (!ModerationState.APPROVED.equals(c.getModerationState())) {
            throw new IllegalStateException("This idea is not open for public support.");
        }
        if (supportRepository.existsByContributionIdAndSupporterKey(contributionId, supporterKey)) {
            return c; // already supported — idempotent
        }
        ContributionSupportEntity s = new ContributionSupportEntity();
        s.setContributionId(contributionId);
        s.setTenantId(tenantId);
        s.setSupporterKey(supporterKey);
        supportRepository.save(s);
        c.setSupportCount((int) supportRepository.countByContributionId(contributionId));
        contributionRepository.save(c);
        contributionService.recordEvent(c, "SUPPORTED", null, null, null, "Support added");
        contributionService.emit(c, "participation.contribution.supported");
        return c;
    }

    /** Withdraw a previously-recorded support (idempotent). */
    @Transactional
    public ContributionEntity unsupport(UUID tenantId, UUID contributionId, String supporterKey) {
        if (supporterKey == null || supporterKey.isBlank()) {
            throw new IllegalArgumentException("A supporter key is required.");
        }
        ContributionEntity c = contributionRepository.findByIdAndTenantId(contributionId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Contribution not found: " + contributionId));
        supportRepository.findByContributionIdAndSupporterKey(contributionId, supporterKey)
                .ifPresent(supportRepository::delete);
        c.setSupportCount((int) supportRepository.countByContributionId(contributionId));
        contributionRepository.save(c);
        return c;
    }
}
