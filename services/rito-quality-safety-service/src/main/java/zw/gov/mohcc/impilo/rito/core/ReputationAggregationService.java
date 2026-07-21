package zw.gov.mohcc.impilo.rito.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.events.RitoEventEmitter;
import zw.gov.mohcc.impilo.rito.persistence.entity.FacilityReputationSummaryEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderRatingEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderReputationSummaryEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.RatingDomainScoreEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.FacilityReputationSummaryRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.ProviderRatingRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.ProviderReputationSummaryRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.RatingDomainScoreRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Recomputes the derived reputation aggregate (RW4) from PUBLISHED ratings + their
 * per-domain scores. For each provider/period it produces per-facility rollups AND an
 * "all facilities" rollup (facility/service-point null), per domain. Public reads use the
 * {@code verified_*} fields only. The summary is never a source of truth — it is fully
 * regenerated from the underlying ratings, so a recompute is idempotent.
 */
@Service
public class ReputationAggregationService {

    private static final Logger log = LoggerFactory.getLogger(ReputationAggregationService.class);
    private static final UUID NIL = new UUID(0L, 0L);

    private final ProviderRatingRepository ratingRepository;
    private final RatingDomainScoreRepository domainScoreRepository;
    private final ProviderReputationSummaryRepository summaryRepository;
    private final FacilityReputationSummaryRepository facilitySummaryRepository;
    private final RitoEventEmitter emitter;

    public ReputationAggregationService(ProviderRatingRepository ratingRepository,
                                        RatingDomainScoreRepository domainScoreRepository,
                                        ProviderReputationSummaryRepository summaryRepository,
                                        FacilityReputationSummaryRepository facilitySummaryRepository,
                                        RitoEventEmitter emitter) {
        this.ratingRepository = ratingRepository;
        this.domainScoreRepository = domainScoreRepository;
        this.summaryRepository = summaryRepository;
        this.facilitySummaryRepository = facilitySummaryRepository;
        this.emitter = emitter;
    }

    /**
     * Recompute the facility-level reputation aggregate (RW8): all PUBLISHED ratings AT a
     * facility, across every provider, per domain. Idempotent upsert; emits rito.reputation.
     */
    @Transactional
    public int recomputeForFacility(UUID tenantId, UUID facilityId, String period) {
        List<ProviderRatingEntity> ratings = ratingRepository
                .findByTenantIdAndFacilityId(tenantId, facilityId).stream()
                .filter(r -> "PUBLISHED".equals(r.getModerationState()))
                .filter(r -> period == null || period.equals(r.getReportingPeriod()))
                .toList();

        Map<String, Acc> byDomainAcc = new LinkedHashMap<>();
        for (ProviderRatingEntity r : ratings) {
            boolean verified = Boolean.TRUE.equals(r.getVerifiedInteraction());
            Map<String, List<BigDecimal>> byDomain = new LinkedHashMap<>();
            for (RatingDomainScoreEntity s : domainScoreRepository.findByRatingId(r.getId())) {
                byDomain.computeIfAbsent(s.getDomain(), d -> new ArrayList<>()).add(s.getScore());
            }
            for (Map.Entry<String, List<BigDecimal>> e : byDomain.entrySet()) {
                Acc a = byDomainAcc.computeIfAbsent(e.getKey(), k -> new Acc());
                BigDecimal domainMean = mean(e.getValue());
                a.count++;
                a.sum = a.sum.add(domainMean);
                int bucket = Math.max(1, Math.min(5, domainMean.setScale(0, RoundingMode.HALF_UP).intValue()));
                a.histogram[bucket]++;
                if (verified) {
                    a.verifiedCount++;
                    a.verifiedSum = a.verifiedSum.add(domainMean);
                }
            }
        }

        for (Map.Entry<String, Acc> e : byDomainAcc.entrySet()) {
            String domain = e.getKey();
            Acc a = e.getValue();
            FacilityReputationSummaryEntity summary = facilitySummaryRepository
                    .findByTenantIdAndFacilityIdAndDomainAndReportingPeriod(tenantId, facilityId, domain, period)
                    .orElseGet(FacilityReputationSummaryEntity::new);
            summary.setTenantId(tenantId);
            summary.setFacilityId(facilityId);
            summary.setDomain(domain);
            summary.setReportingPeriod(period);
            summary.setRatingCount(a.count);
            summary.setVerifiedCount(a.verifiedCount);
            summary.setMeanScore(a.count > 0 ? a.sum.divide(BigDecimal.valueOf(a.count), 2, RoundingMode.HALF_UP) : null);
            summary.setVerifiedMeanScore(a.verifiedCount > 0
                    ? a.verifiedSum.divide(BigDecimal.valueOf(a.verifiedCount), 2, RoundingMode.HALF_UP) : null);
            summary.setDistribution(histogramJson(a.histogram));
            summary.setLastRecomputedAt(java.time.OffsetDateTime.now());
            facilitySummaryRepository.save(summary);
        }

        emitter.emit("REPUTATION", facilityId.toString(), "rito.reputation.facility_recomputed",
                "FACILITY", facilityId.toString(),
                Map.of("facilityId", facilityId.toString(),
                        "period", period != null ? period : "ALL", "domains", byDomainAcc.size()),
                tenantId);
        log.info("Facility reputation recomputed facility={} period={} domains={}",
                facilityId, period, byDomainAcc.size());
        return byDomainAcc.size();
    }

    private static final class Acc {
        int count, verifiedCount;
        BigDecimal sum = BigDecimal.ZERO, verifiedSum = BigDecimal.ZERO;
        final int[] histogram = new int[6]; // buckets 1..5 (index 0 unused)
    }

    private record Key(UUID facilityId, UUID servicePointId, String domain) {}

    @Transactional
    public int recomputeForProvider(UUID tenantId, String providerPublicId, String period) {
        List<ProviderRatingEntity> ratings = ratingRepository
                .findByTenantIdAndProviderPublicId(tenantId, providerPublicId).stream()
                .filter(r -> "PUBLISHED".equals(r.getModerationState()))
                .filter(r -> period == null || period.equals(r.getReportingPeriod()))
                .toList();

        Map<Key, Acc> accumulators = new LinkedHashMap<>();
        for (ProviderRatingEntity r : ratings) {
            boolean verified = Boolean.TRUE.equals(r.getVerifiedInteraction());
            // A rating's score within a domain = the mean of its measures in that domain.
            Map<String, List<BigDecimal>> byDomain = new LinkedHashMap<>();
            for (RatingDomainScoreEntity s : domainScoreRepository.findByRatingId(r.getId())) {
                byDomain.computeIfAbsent(s.getDomain(), d -> new ArrayList<>()).add(s.getScore());
            }
            for (Map.Entry<String, List<BigDecimal>> e : byDomain.entrySet()) {
                BigDecimal domainMean = mean(e.getValue());
                // The "all facilities" rollup always gets it (key facility/service-point = null);
                // a per-facility bucket is added only when a facility is actually known, so a
                // null-facility rating is not double-counted into the same key.
                accumulate(accumulators, new Key(null, null, e.getKey()), domainMean, verified);
                if (r.getFacilityId() != null) {
                    accumulate(accumulators, new Key(r.getFacilityId(), r.getServicePointId(), e.getKey()), domainMean, verified);
                }
            }
        }

        // Index existing summaries for this provider/period by their folded key for upsert.
        Map<String, ProviderReputationSummaryEntity> existing = new HashMap<>();
        for (ProviderReputationSummaryEntity s : summaryRepository
                .findByTenantIdAndProviderPublicId(tenantId, providerPublicId)) {
            if (period == null || period.equals(s.getReportingPeriod())) {
                existing.put(foldKey(s.getFacilityId(), s.getServicePointId(), s.getDomain(), s.getReportingPeriod()), s);
            }
        }

        String effectivePeriod = period;
        for (Map.Entry<Key, Acc> e : accumulators.entrySet()) {
            Key k = e.getKey();
            Acc a = e.getValue();
            String fold = foldKey(k.facilityId(), k.servicePointId(), k.domain(), effectivePeriod);
            ProviderReputationSummaryEntity summary = existing.get(fold);
            if (summary == null) {
                summary = new ProviderReputationSummaryEntity();
                summary.setTenantId(tenantId);
                summary.setProviderPublicId(providerPublicId);
                summary.setFacilityId(k.facilityId());
                summary.setServicePointId(k.servicePointId());
                summary.setDomain(k.domain());
                summary.setReportingPeriod(effectivePeriod);
            }
            summary.setRatingCount(a.count);
            summary.setVerifiedCount(a.verifiedCount);
            summary.setMeanScore(a.count > 0 ? a.sum.divide(BigDecimal.valueOf(a.count), 2, RoundingMode.HALF_UP) : null);
            summary.setVerifiedMeanScore(a.verifiedCount > 0
                    ? a.verifiedSum.divide(BigDecimal.valueOf(a.verifiedCount), 2, RoundingMode.HALF_UP) : null);
            summary.setDistribution(histogramJson(a.histogram));
            summary.setLastRecomputedAt(java.time.OffsetDateTime.now());
            summaryRepository.save(summary);
        }

        emitter.emit("REPUTATION", providerPublicId, "rito.reputation.recomputed",
                "PROVIDER", providerPublicId,
                Map.of("providerPublicId", providerPublicId,
                        "period", effectivePeriod != null ? effectivePeriod : "ALL",
                        "summaries", accumulators.size()),
                tenantId);

        log.info("Reputation recomputed provider={} period={} summaries={}",
                providerPublicId, effectivePeriod, accumulators.size());
        return accumulators.size();
    }

    private static void accumulate(Map<Key, Acc> acc, Key key, BigDecimal domainMean, boolean verified) {
        Acc a = acc.computeIfAbsent(key, k -> new Acc());
        a.count++;
        a.sum = a.sum.add(domainMean);
        int bucket = Math.max(1, Math.min(5, domainMean.setScale(0, RoundingMode.HALF_UP).intValue()));
        a.histogram[bucket]++;
        if (verified) {
            a.verifiedCount++;
            a.verifiedSum = a.verifiedSum.add(domainMean);
        }
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            sum = sum.add(v);
        }
        return sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private static String foldKey(UUID facilityId, UUID servicePointId, String domain, String period) {
        return Objects.toString(facilityId, NIL.toString()) + "|"
                + Objects.toString(servicePointId, NIL.toString()) + "|"
                + domain + "|" + Objects.toString(period, "");
    }

    private static String histogramJson(int[] histogram) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 1; i <= 5; i++) {
            if (i > 1) {
                sb.append(',');
            }
            sb.append('"').append(i).append("\":").append(histogram[i]);
        }
        return sb.append('}').toString();
    }
}
