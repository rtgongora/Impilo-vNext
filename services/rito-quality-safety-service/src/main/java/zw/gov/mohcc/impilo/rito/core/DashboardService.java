package zw.gov.mohcc.impilo.rito.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.domain.CaseLifecycle;
import zw.gov.mohcc.impilo.rito.persistence.entity.AuditEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.CorrectiveActionEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.AuditRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.CaseRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.CorrectiveActionRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.SafetyIncidentRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.SurveyResponseRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only dashboard aggregations for Rito. Every figure is computed from the repositories
 * over real persisted rows — empty datasets produce honest zeros and {@code null} averages,
 * never fabricated numbers. The four views correspond to the operating audiences: a single
 * facility, the above-site (district/province/national) rollup, the client-voice pillar, and
 * the patient-safety pillar.
 */
@Service
public class DashboardService {

    private static final Set<String> TERMINAL = CaseLifecycle.TERMINAL;
    private static final Set<String> CA_TERMINAL = Set.of("COMPLETED", "VERIFIED", "CANCELLED");
    private static final Set<String> CLIENT_VOICE_TYPES =
            Set.of("COMPLAINT", "COMPLIMENT", "SUGGESTION", "SATISFACTION_SURVEY");
    private static final Set<String> SAFETY_TYPES =
            Set.of("SAFETY_CONCERN", "SAFETY_INCIDENT", "NEAR_MISS", "ADVERSE_EVENT");

    private final CaseRepository caseRepository;
    private final CorrectiveActionRepository correctiveActionRepository;
    private final AuditRepository auditRepository;
    private final SafetyIncidentRepository safetyIncidentRepository;
    private final SurveyResponseRepository surveyResponseRepository;

    public DashboardService(CaseRepository caseRepository,
                            CorrectiveActionRepository correctiveActionRepository,
                            AuditRepository auditRepository,
                            SafetyIncidentRepository safetyIncidentRepository,
                            SurveyResponseRepository surveyResponseRepository) {
        this.caseRepository = caseRepository;
        this.correctiveActionRepository = correctiveActionRepository;
        this.auditRepository = auditRepository;
        this.safetyIncidentRepository = safetyIncidentRepository;
        this.surveyResponseRepository = surveyResponseRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> facilityDashboard(UUID tenantId, UUID facilityId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<CaseEntity> cases = caseRepository.findByTenantIdAndFacilityId(tenantId, facilityId);

        long overdue = cases.stream()
                .filter(c -> c.getDueAt() != null && c.getDueAt().isBefore(now))
                .filter(c -> !TERMINAL.contains(c.getStatus()))
                .count();

        long capaOpen = correctiveActionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(ca -> Objects.equals(ca.getFacilityId(), facilityId))
                .filter(ca -> !CA_TERMINAL.contains(ca.getStatus()))
                .count();

        List<AuditEntity> audits = auditRepository.findByTenantIdAndFacilityId(tenantId, facilityId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("facilityId", facilityId != null ? facilityId.toString() : null);
        out.put("totalCases", (long) cases.size());
        out.put("byStatus", countBy(cases, CaseEntity::getStatus));
        out.put("bySeverity", countBy(cases, CaseEntity::getSeverity));
        out.put("openCases", openCount(cases));
        out.put("overdueCases", overdue);
        out.put("openCorrectiveActions", capaOpen);
        Map<String, Object> auditBlock = new LinkedHashMap<>();
        auditBlock.put("total", (long) audits.size());
        auditBlock.put("byStatus", countBy(audits, AuditEntity::getStatus));
        out.put("audits", auditBlock);
        out.put("averageSatisfactionScore", averageSatisfaction(cases));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> aboveSiteDashboard(UUID tenantId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<CaseEntity> cases = caseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<CorrectiveActionEntity> capas = correctiveActionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<AuditEntity> audits = auditRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        long overdue = cases.stream()
                .filter(c -> c.getDueAt() != null && c.getDueAt().isBefore(now))
                .filter(c -> !TERMINAL.contains(c.getStatus()))
                .count();
        long sentinel = safetyIncidentRepository.findByTenantIdAndSentinelTrue(tenantId).size();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalCases", (long) cases.size());
        out.put("byStatus", countBy(cases, CaseEntity::getStatus));
        out.put("bySeverity", countBy(cases, CaseEntity::getSeverity));
        out.put("openCases", openCount(cases));
        out.put("overdueCases", overdue);

        Map<String, Object> capaBlock = new LinkedHashMap<>();
        capaBlock.put("total", (long) capas.size());
        capaBlock.put("byStatus", countBy(capas, CorrectiveActionEntity::getStatus));
        out.put("correctiveActions", capaBlock);

        Map<String, Object> auditBlock = new LinkedHashMap<>();
        auditBlock.put("total", (long) audits.size());
        auditBlock.put("byStatus", countBy(audits, AuditEntity::getStatus));
        out.put("audits", auditBlock);

        Map<String, Object> safetyBlock = new LinkedHashMap<>();
        long safetyCases = cases.stream().filter(c -> SAFETY_TYPES.contains(c.getCaseType())).count();
        safetyBlock.put("total", safetyCases);
        safetyBlock.put("sentinelCount", sentinel);
        out.put("safetyIncidents", safetyBlock);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> clientVoiceDashboard(UUID tenantId) {
        List<CaseEntity> cases = caseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(c -> CLIENT_VOICE_TYPES.contains(c.getCaseType()))
                .collect(Collectors.toList());

        long complaintsOpen = cases.stream()
                .filter(c -> "COMPLAINT".equals(c.getCaseType()))
                .filter(c -> !TERMINAL.contains(c.getStatus()))
                .count();

        BigDecimal avgSatisfaction = averageSatisfaction(cases.stream()
                .filter(c -> CaseLifecycle.CLOSED.equals(c.getStatus()))
                .collect(Collectors.toList()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalCases", (long) cases.size());
        out.put("byType", countBy(cases, CaseEntity::getCaseType));
        out.put("openCases", openCount(cases));
        out.put("complaintsOpen", complaintsOpen);
        out.put("averageSatisfactionScore", avgSatisfaction);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> safetyDashboard(UUID tenantId) {
        List<CaseEntity> cases = caseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(c -> SAFETY_TYPES.contains(c.getCaseType()))
                .collect(Collectors.toList());

        long criticalOpen = cases.stream()
                .filter(c -> "CRITICAL".equals(c.getSeverity()))
                .filter(c -> !TERMINAL.contains(c.getStatus()))
                .count();
        long sentinel = safetyIncidentRepository.findByTenantIdAndSentinelTrue(tenantId).size();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalCases", (long) cases.size());
        out.put("byType", countBy(cases, CaseEntity::getCaseType));
        out.put("bySeverity", countBy(cases, CaseEntity::getSeverity));
        out.put("openCases", openCount(cases));
        out.put("criticalOpen", criticalOpen);
        out.put("sentinelCount", sentinel);
        return out;
    }

    // ---- internal helpers ----

    private static <T> Map<String, Long> countBy(List<T> items, Function<T, String> key) {
        return items.stream()
                .map(key)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private static long openCount(List<CaseEntity> cases) {
        return cases.stream().filter(c -> !TERMINAL.contains(c.getStatus())).count();
    }

    private static BigDecimal averageSatisfaction(List<CaseEntity> cases) {
        long count = 0;
        long sum = 0;
        for (CaseEntity c : cases) {
            if (c.getSatisfactionScore() != null) {
                sum += c.getSatisfactionScore();
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
