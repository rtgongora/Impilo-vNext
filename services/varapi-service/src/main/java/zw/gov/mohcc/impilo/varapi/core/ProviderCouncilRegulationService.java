package zw.gov.mohcc.impilo.varapi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.integration.CouncilRegulatoryPolicyClient;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.FundoLearningLinkEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderApplicationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilProfileEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilReviewEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.FundoLearningLinkRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderApplicationRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilProfileRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilReviewRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ProviderCouncilRegulationService {

    private final ProviderCouncilProfileRepository profileRepository;
    private final ProviderRepository providerRepository;
    private final CouncilRepository councilRepository;
    private final FundoLearningLinkRepository fundoLearningLinkRepository;
    private final ProviderCouncilReviewRepository reviewRepository;
    private final ProviderApplicationRepository applicationRepository;
    private final CouncilRegulatoryPolicyClient policyClient;

    public ProviderCouncilRegulationService(
            ProviderCouncilProfileRepository profileRepository,
            ProviderRepository providerRepository,
            CouncilRepository councilRepository,
            FundoLearningLinkRepository fundoLearningLinkRepository,
            ProviderCouncilReviewRepository reviewRepository,
            ProviderApplicationRepository applicationRepository,
            CouncilRegulatoryPolicyClient policyClient) {
        this.profileRepository = profileRepository;
        this.providerRepository = providerRepository;
        this.councilRepository = councilRepository;
        this.fundoLearningLinkRepository = fundoLearningLinkRepository;
        this.reviewRepository = reviewRepository;
        this.applicationRepository = applicationRepository;
        this.policyClient = policyClient;
    }

    @Transactional(readOnly = true)
    public List<ProviderCouncilProfileEntity> listProfiles(Long providerId) {
        TrustContext ctx = TrustContextHolder.require();
        requireTenant(ctx);
        requireProvider(providerId, ctx.tenantId());
        return profileRepository.findByTenantIdAndProvider_Id(ctx.tenantId(), providerId);
    }

    @Transactional
    public ProviderCouncilProfileEntity upsertProfile(
            Long providerId,
            Long councilId,
            String registrationCategory,
            String professionalClass,
            String status,
            boolean primaryCouncil,
            LocalDate startDate,
            LocalDate endDate,
            String metadata) {
        TrustContext ctx = TrustContextHolder.require();
        requireTenant(ctx);
        if (!policyClient.isAllowed("UPSERT_COUNCIL_PROFILE", councilId, null, providerId)) {
            throw new IllegalStateException("POLICY_DENIED");
        }
        ProviderEntity provider = requireProvider(providerId, ctx.tenantId());
        CouncilEntity council = councilRepository.findByIdAndTenantId(councilId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Council not found: " + councilId));

        ProviderCouncilProfileEntity row = profileRepository
                .findByTenantIdAndProvider_Id(ctx.tenantId(), providerId).stream()
                .filter(p -> p.getCouncil().getId().equals(councilId))
                .findFirst()
                .orElseGet(ProviderCouncilProfileEntity::new);

        row.setTenantId(ctx.tenantId());
        row.setProvider(provider);
        row.setCouncil(council);
        row.setRegistrationCategory(registrationCategory);
        row.setProfessionalClass(professionalClass);
        if (status != null) {
            row.setStatus(status);
        }
        row.setPrimaryCouncilFlag(primaryCouncil);
        row.setStartDate(startDate);
        row.setEndDate(endDate);
        if (metadata != null) {
            row.setMetadata(metadata);
        }
        return profileRepository.save(row);
    }

    @Transactional
    public FundoLearningLinkEntity upsertFundoLink(Long providerId, String fundoUserRef, String linkStatus) {
        TrustContext ctx = TrustContextHolder.require();
        requireTenant(ctx);
        if (!policyClient.isAllowed("UPSERT_FUNDO_LINK", null, null, providerId)) {
            throw new IllegalStateException("POLICY_DENIED");
        }
        ProviderEntity provider = requireProvider(providerId, ctx.tenantId());
        FundoLearningLinkEntity row = fundoLearningLinkRepository
                .findByTenantIdAndProvider_Id(ctx.tenantId(), providerId)
                .orElseGet(FundoLearningLinkEntity::new);
        row.setTenantId(ctx.tenantId());
        row.setProvider(provider);
        row.setFundoUserRef(fundoUserRef);
        if (linkStatus != null) {
            row.setLinkStatus(linkStatus);
        }
        if ("LINKED".equalsIgnoreCase(linkStatus)) {
            row.setLinkedAt(Instant.now());
        }
        return fundoLearningLinkRepository.save(row);
    }

    @Transactional
    public ProviderCouncilReviewEntity recordReview(
            Long applicationId,
            Long councilId,
            String reviewType,
            String decision,
            String notes,
            String resolutionReference) {
        TrustContext ctx = TrustContextHolder.require();
        requireTenant(ctx);
        if (!policyClient.isAllowed("RECORD_COUNCIL_REVIEW", councilId, applicationId, null)) {
            throw new IllegalStateException("POLICY_DENIED");
        }
        ProviderCouncilReviewEntity row = new ProviderCouncilReviewEntity();
        row.setTenantId(ctx.tenantId());
        if (applicationId != null) {
            ProviderApplicationEntity app = applicationRepository.findByIdAndTenantId(applicationId, ctx.tenantId())
                    .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
            row.setApplication(app);
            app.setReviewState(decision);
            applicationRepository.save(app);
        }
        if (councilId != null) {
            CouncilEntity council = councilRepository.findByIdAndTenantId(councilId, ctx.tenantId())
                    .orElseThrow(() -> new IllegalArgumentException("Council not found: " + councilId));
            row.setCouncil(council);
        }
        row.setReviewType(reviewType);
        row.setReviewerActorId(ctx.actorId() != null ? ctx.actorId() : "UNKNOWN");
        row.setDecision(decision);
        row.setNotes(notes);
        row.setResolutionReference(resolutionReference);
        return reviewRepository.save(row);
    }

    @Transactional(readOnly = true)
    public List<ProviderCouncilReviewEntity> listReviews(Long applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        requireTenant(ctx);
        return reviewRepository.findByTenantIdAndApplication_IdOrderByReviewedAtDesc(ctx.tenantId(), applicationId);
    }

    private ProviderEntity requireProvider(Long providerId, UUID tenantId) {
        return providerRepository.findByIdAndTenantId(providerId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));
    }

    private static void requireTenant(TrustContext ctx) {
        if (ctx.tenantId() == null) {
            throw new IllegalStateException("Tenant is required");
        }
    }
}
