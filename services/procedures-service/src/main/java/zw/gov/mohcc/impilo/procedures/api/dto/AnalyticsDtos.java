package zw.gov.mohcc.impilo.procedures.api.dto;

import java.util.List;

/** Shapes for the pipeline analytics indicator catalogue (§26). */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record IndicatorView(
            String indicatorCode,
            String indicatorName,
            String numeratorDescription,
            String denominatorDescription,
            String computationStatus,
            String executableVia,
            String gapReason,
            String owningService,
            boolean delegatedOutOfScope,
            String sourceCitation) {
    }

    public record IndicatorSummary(
            int total,
            int computed,
            int partial,
            int notYetInstrumented,
            List<IndicatorView> indicators) {
    }
}
