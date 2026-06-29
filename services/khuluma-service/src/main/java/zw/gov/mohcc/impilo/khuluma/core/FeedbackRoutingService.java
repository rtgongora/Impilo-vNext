package zw.gov.mohcc.impilo.khuluma.core;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Feedback routing (G-KH-05/06, closes the G-RT-03 learning loop). Decides whether a piece of client
 * feedback (from a Khuluma conversation or a Nompilo handoff) is a quality/safety concern that should be
 * routed to Rito as a quality-signal, and builds the exact {@code POST /internal/v1/rito/quality-signals}
 * body. Pure decision + contract mapping — the HTTP post is performed by a thin client at the call site.
 */
@Service
public class FeedbackRoutingService {

    /** The kind of feedback. Quality/safety kinds feed the Rito learning loop. */
    public enum FeedbackKind {
        COMPLAINT, SAFETY_CONCERN, QUALITY_ISSUE, ADVERSE_EXPERIENCE, PRAISE, QUERY, OTHER
    }

    /** Kinds that are routed to Rito's quality/safety pillar. */
    private static final Set<FeedbackKind> ROUTABLE = Set.of(
            FeedbackKind.COMPLAINT, FeedbackKind.SAFETY_CONCERN,
            FeedbackKind.QUALITY_ISSUE, FeedbackKind.ADVERSE_EXPERIENCE);

    /**
     * @param routeToRito whether this feedback should be sent to Rito
     * @param signalBody  the rito quality-signal request body (null when not routable)
     * @param reason      auditable reason code
     */
    public record RoutingDecision(boolean routeToRito, Map<String, Object> signalBody, String reason) {
    }

    /**
     * Decide whether a feedback item routes to Rito and build its quality-signal body.
     *
     * @param kind      the feedback kind (null → OTHER)
     * @param severity  caller-supplied severity (LOW/MODERATE/HIGH/CRITICAL); optional
     * @param sourceRef the originating reference (e.g. khuluma conversation id or handoff id)
     * @param summary   a short, non-sensitive summary of the concern
     * @param sourceSystem the originating system ("khuluma" | "nompilo")
     */
    public RoutingDecision route(FeedbackKind kind, String severity, String sourceRef,
                                 String summary, String sourceSystem) {
        FeedbackKind k = kind == null ? FeedbackKind.OTHER : kind;
        if (!ROUTABLE.contains(k)) {
            return new RoutingDecision(false, null, "NOT_A_QUALITY_SAFETY_CONCERN");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signal_type", k.name());
        body.put("source_system", (sourceSystem == null || sourceSystem.isBlank()) ? "khuluma" : sourceSystem);
        if (sourceRef != null) body.put("source_ref", sourceRef);
        if (severity != null) body.put("severity", severity);
        if (summary != null) body.put("summary", summary);
        return new RoutingDecision(true, body, "ROUTED_TO_RITO");
    }
}
