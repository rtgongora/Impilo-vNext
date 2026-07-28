package zw.gov.mohcc.impilo.surgery.api.dto;

import java.util.List;

/** Shapes for the surgical-pack analytics indicator catalogue (§23). */
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
            String lancetCoreIndicator) {
    }

    public record IndicatorSummary(
            int total,
            int computed,
            int partial,
            int notYetInstrumented,
            List<IndicatorView> indicators) {
    }
}
