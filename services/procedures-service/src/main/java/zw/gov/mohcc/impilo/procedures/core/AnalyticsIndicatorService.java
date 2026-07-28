package zw.gov.mohcc.impilo.procedures.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.procedures.api.dto.AnalyticsDtos.IndicatorSummary;
import zw.gov.mohcc.impilo.procedures.api.dto.AnalyticsDtos.IndicatorView;
import zw.gov.mohcc.impilo.procedures.persistence.entity.AnalyticsIndicatorDefinitionEntity;
import zw.gov.mohcc.impilo.procedures.persistence.repository.AnalyticsIndicatorDefinitionRepository;

import java.util.List;
import java.util.UUID;

/**
 * Read access to the pipeline analytics indicator catalogue (§26).
 *
 * <p>Content, not engine — same reason §9/§10/§15/§17/§18/§21 are content (see this programme's
 * other core services). This service does NOT compute a single number: every indicator's
 * {@code computationStatus} and {@code executableVia}/{@code gapReason} are governed content
 * seeded by V010, declaring today's real state rather than recomputing what reporting-service's
 * proven {@code TheatreReportingConsumer} projection already answers, or fabricating a number for
 * what nothing computes yet.</p>
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
                .orElseThrow(() -> new ProceduresDomainException(
                        "ANALYTICS_INDICATOR_NOT_FOUND", 404,
                        "No analytics indicator '" + indicatorCode + "'"));
        return toView(e);
    }

    private IndicatorView toView(AnalyticsIndicatorDefinitionEntity e) {
        return new IndicatorView(e.getIndicatorCode(), e.getIndicatorName(), e.getNumeratorDescription(),
                e.getDenominatorDescription(), e.getComputationStatus(), e.getExecutableVia(),
                e.getGapReason(), e.getOwningService(), e.isDelegatedOutOfScope(), e.getSourceCitation());
    }
}
