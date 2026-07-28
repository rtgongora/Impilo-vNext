package zw.gov.mohcc.impilo.surgery.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surgery.api.dto.AnalyticsDtos.IndicatorSummary;
import zw.gov.mohcc.impilo.surgery.api.dto.AnalyticsDtos.IndicatorView;
import zw.gov.mohcc.impilo.surgery.persistence.entity.AnalyticsIndicatorDefinitionEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.AnalyticsIndicatorDefinitionRepository;

import java.util.List;
import java.util.UUID;

/**
 * Read access to the surgical-pack analytics indicator catalogue (§23).
 *
 * <p>Content, not engine — same reason S1-SB-4's own core services are all content/reference,
 * never a second execution path. This service does NOT compute a single number: every
 * indicator's {@code computationStatus} and {@code executableVia}/{@code gapReason} are governed
 * content seeded by V008, declaring today's real state against reporting-service's proven
 * {@code rpt_theatre_case_metric} projection rather than recomputing it or fabricating a number
 * for what nothing computes yet.</p>
 */
@Service
public class AnalyticsIndicatorService {

    private final AnalyticsIndicatorDefinitionRepository repository;

    public AnalyticsIndicatorService(AnalyticsIndicatorDefinitionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public IndicatorSummary indicatorCatalogue(UUID tenantId) {
        List<IndicatorView> views = repository.findByTenantIdOrderByDisplayOrderAsc(tenantId).stream()
                .map(this::toView)
                .toList();
        int computed = (int) views.stream().filter(v -> "COMPUTED".equals(v.computationStatus())).count();
        int partial = (int) views.stream().filter(v -> "PARTIAL".equals(v.computationStatus())).count();
        int notInstrumented = (int) views.stream()
                .filter(v -> "NOT_YET_INSTRUMENTED".equals(v.computationStatus())).count();
        return new IndicatorSummary(views.size(), computed, partial, notInstrumented, views);
    }

    @Transactional(readOnly = true)
    public IndicatorView indicator(UUID tenantId, String indicatorCode) {
        AnalyticsIndicatorDefinitionEntity e = repository
                .findByTenantIdAndIndicatorCode(tenantId, indicatorCode)
                .orElseThrow(() -> new SurgeryDomainException(
                        "ANALYTICS_INDICATOR_NOT_FOUND", 404,
                        "No surgical analytics indicator '" + indicatorCode + "'"));
        return toView(e);
    }

    private IndicatorView toView(AnalyticsIndicatorDefinitionEntity e) {
        return new IndicatorView(e.getIndicatorCode(), e.getIndicatorName(), e.getNumeratorDescription(),
                e.getDenominatorDescription(), e.getComputationStatus(), e.getExecutableVia(),
                e.getGapReason(), e.getOwningService(), e.isDelegatedOutOfScope(),
                e.getLancetCoreIndicator());
    }
}
