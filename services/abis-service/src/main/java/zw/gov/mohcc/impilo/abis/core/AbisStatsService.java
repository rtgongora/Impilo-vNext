package zw.gov.mohcc.impilo.abis.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.abis.config.AbisThresholdProperties;
import zw.gov.mohcc.impilo.abis.persistence.entity.AdjudicationCaseEntity;
import zw.gov.mohcc.impilo.abis.persistence.repository.AdjudicationCaseRepository;
import zw.gov.mohcc.impilo.abis.persistence.repository.TemplateRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only ABIS operational stats for the console dashboards (W3e): template volumes by
 * modality/status, quality-score distribution, and adjudication queue counts.
 *
 * <p>ROC / FMR-FNMR calibration needs a labelled evaluation corpus (external) — NOT derived
 * here; this surface reports live operational volumes only, honestly.</p>
 */
@Service
public class AbisStatsService {

    private final TemplateRepository templateRepository;
    private final AdjudicationCaseRepository caseRepository;
    private final AbisThresholdProperties thresholds;

    public AbisStatsService(TemplateRepository templateRepository,
                            AdjudicationCaseRepository caseRepository,
                            AbisThresholdProperties thresholds) {
        this.templateRepository = templateRepository;
        this.caseRepository = caseRepository;
        this.thresholds = thresholds;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats(UUID tenantId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tenantId", tenantId.toString());

        // Templates: total + by (modality, status).
        Map<String, Object> templates = new LinkedHashMap<>();
        templates.put("total", templateRepository.countByTenantId(tenantId));
        templates.put("active", templateRepository.countByTenantIdAndStatus(tenantId, "ACTIVE"));
        Map<String, Map<String, Long>> byModality = new LinkedHashMap<>();
        for (Object[] row : templateRepository.countByModalityAndStatus(tenantId)) {
            String modality = String.valueOf(row[0]);
            String status = String.valueOf(row[1]);
            long count = ((Number) row[2]).longValue();
            byModality.computeIfAbsent(modality, k -> new LinkedHashMap<>()).put(status, count);
        }
        templates.put("byModalityStatus", byModality);
        out.put("templates", templates);

        // Quality-score histogram buckets (0,20,40,60,80) for ACTIVE templates.
        Map<String, Long> quality = new LinkedHashMap<>();
        quality.put("0-19", 0L);
        quality.put("20-39", 0L);
        quality.put("40-59", 0L);
        quality.put("60-79", 0L);
        quality.put("80-100", 0L);
        for (Object[] row : templateRepository.qualityHistogram(tenantId)) {
            int bucket = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            quality.put(bucketLabel(bucket), count);
        }
        out.put("qualityDistribution", quality);

        // Adjudication queue counts by status.
        Map<String, Long> adjudication = new LinkedHashMap<>();
        adjudication.put("OPEN", 0L);
        adjudication.put("IN_REVIEW", 0L);
        adjudication.put("RESOLVED", 0L);
        for (Object[] row : caseRepository.countByStatus(tenantId)) {
            adjudication.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        out.put("adjudication", adjudication);

        out.put("thresholds", thresholdView());
        out.put("calibrationNote",
                "ROC/FMR-FNMR calibration requires a labelled evaluation corpus (external); "
                        + "operational volumes only are reported here.");
        return out;
    }

    /** Governed thresholds surfaced read-only for the console (W3e). */
    public Map<String, Object> thresholdView() {
        Map<String, Object> t = new LinkedHashMap<>();
        Map<String, Object> dedup = new LinkedHashMap<>();
        dedup.put("enabled", thresholds.getDedup().isEnabled());
        dedup.put("threshold", thresholds.getDedup().getThreshold());
        dedup.put("maxGallery", thresholds.getDedup().getMaxGallery());
        t.put("dedup", dedup);
        t.put("qualityMinScore", thresholds.getQuality().getMinScore());
        t.put("scope", "GLOBAL");
        t.put("note", "Per-tenant threshold overrides are a documented follow-on; values are global.");
        return t;
    }

    private static String bucketLabel(int bucket) {
        return switch (bucket) {
            case 80 -> "80-100";
            case 60 -> "60-79";
            case 40 -> "40-59";
            case 20 -> "20-39";
            default -> "0-19";
        };
    }
}
