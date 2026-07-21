package zw.gov.mohcc.impilo.rito.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.*;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderRatingEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderReputationSummaryEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.ProviderRatingRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.ProviderReputationSummaryRepository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Governed reads over the reputation aggregate (RW5). Two lanes:
 * <ul>
 *   <li><b>Public verified-experience</b> — verified fields, experience domains only, no
 *       respondent identity. Source-tagged "Rito"; VARAPI/TUSO compose it read-only.</li>
 *   <li><b>Authorised management</b> — all four domains + overall means + moderation
 *       counts (served on the internal, TSHEPO-gated lane).</li>
 * </ul>
 */
@Service
public class ReputationReadService {

    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyy-MM");
    /** Only the experience domains are publicly visible; clinical/safety are restricted. */
    private static final Set<String> PUBLIC_DOMAINS = Set.of("CLIENT_EXPERIENCE", "ACCESS_PROCESS");

    private final ProviderReputationSummaryRepository summaryRepository;
    private final zw.gov.mohcc.impilo.rito.persistence.repository.FacilityReputationSummaryRepository facilitySummaryRepository;
    private final ProviderRatingRepository ratingRepository;

    public ReputationReadService(
            ProviderReputationSummaryRepository summaryRepository,
            zw.gov.mohcc.impilo.rito.persistence.repository.FacilityReputationSummaryRepository facilitySummaryRepository,
            ProviderRatingRepository ratingRepository) {
        this.summaryRepository = summaryRepository;
        this.facilitySummaryRepository = facilitySummaryRepository;
        this.ratingRepository = ratingRepository;
    }

    @Transactional(readOnly = true)
    public FacilityExperienceSummaryResponse publicFacilityExperienceSummary(UUID tenantId, UUID facilityId, String period) {
        String p = period != null ? period : OffsetDateTime.now().format(PERIOD);
        List<PublicDomainSummary> domains = facilitySummaryRepository
                .findByTenantIdAndFacilityIdAndReportingPeriod(tenantId, facilityId, p).stream()
                .filter(s -> PUBLIC_DOMAINS.contains(s.getDomain()))
                .map(s -> new PublicDomainSummary(s.getDomain(), s.getVerifiedMeanScore(),
                        s.getVerifiedCount() != null ? s.getVerifiedCount() : 0, s.getDistribution()))
                .sorted(Comparator.comparing(PublicDomainSummary::domain))
                .toList();
        int verifiedTotal = (int) ratingRepository.findByTenantIdAndFacilityId(tenantId, facilityId).stream()
                .filter(r -> "PUBLISHED".equals(r.getModerationState()))
                .filter(r -> p.equals(r.getReportingPeriod()))
                .filter(r -> Boolean.TRUE.equals(r.getVerifiedInteraction()))
                .map(ProviderRatingEntity::getEncounterRef)
                .filter(java.util.Objects::nonNull).distinct().count();
        return new FacilityExperienceSummaryResponse(facilityId, p, "Rito", verifiedTotal, domains);
    }

    @Transactional(readOnly = true)
    public ExperienceSummaryResponse publicExperienceSummary(UUID tenantId, String providerPublicId, String period) {
        String p = period != null ? period : OffsetDateTime.now().format(PERIOD);
        List<PublicDomainSummary> domains = allFacilitiesRollup(tenantId, providerPublicId, p).stream()
                .filter(s -> PUBLIC_DOMAINS.contains(s.getDomain()))
                .map(s -> new PublicDomainSummary(s.getDomain(), s.getVerifiedMeanScore(),
                        s.getVerifiedCount() != null ? s.getVerifiedCount() : 0, s.getDistribution()))
                .sorted(Comparator.comparing(PublicDomainSummary::domain))
                .toList();
        int verifiedTotal = (int) publishedRatings(tenantId, providerPublicId, p).stream()
                .filter(r -> Boolean.TRUE.equals(r.getVerifiedInteraction())).count();
        return new ExperienceSummaryResponse(providerPublicId, p, "Rito", verifiedTotal, domains);
    }

    @Transactional(readOnly = true)
    public ManagementViewResponse managementView(UUID tenantId, String providerPublicId, String period) {
        String p = period != null ? period : OffsetDateTime.now().format(PERIOD);
        List<ManagementDomainSummary> domains = allFacilitiesRollup(tenantId, providerPublicId, p).stream()
                .map(s -> new ManagementDomainSummary(s.getDomain(), s.getMeanScore(),
                        s.getRatingCount() != null ? s.getRatingCount() : 0, s.getVerifiedMeanScore(),
                        s.getVerifiedCount() != null ? s.getVerifiedCount() : 0, s.getDistribution()))
                .sorted(Comparator.comparing(ManagementDomainSummary::domain))
                .toList();
        Map<String, Long> moderationCounts = ratingRepository
                .findByTenantIdAndProviderPublicId(tenantId, providerPublicId).stream()
                .filter(r -> p.equals(r.getReportingPeriod()))
                .collect(Collectors.groupingBy(ProviderRatingEntity::getModerationState, Collectors.counting()));
        return new ManagementViewResponse(providerPublicId, p, domains, moderationCounts);
    }

    private List<ProviderReputationSummaryEntity> allFacilitiesRollup(UUID tenantId, String providerPublicId, String period) {
        return summaryRepository
                .findByTenantIdAndProviderPublicIdAndFacilityIdIsNullAndServicePointIdIsNullAndReportingPeriod(
                        tenantId, providerPublicId, period);
    }

    private List<ProviderRatingEntity> publishedRatings(UUID tenantId, String providerPublicId, String period) {
        return ratingRepository.findByTenantIdAndProviderPublicId(tenantId, providerPublicId).stream()
                .filter(r -> "PUBLISHED".equals(r.getModerationState()))
                .filter(r -> period.equals(r.getReportingPeriod()))
                .toList();
    }
}
