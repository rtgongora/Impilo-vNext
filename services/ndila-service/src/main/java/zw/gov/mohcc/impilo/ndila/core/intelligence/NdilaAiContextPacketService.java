package zw.gov.mohcc.impilo.ndila.core.intelligence;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaOperationContext;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaTshepoGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Produces AI-safe context packets for Nompilo and approved AI/LLM assistants.
 *
 * <p>Packets always include:
 * <ul>
 *   <li>a plain-language summary</li>
 *   <li>a structured list of signals (typed + counted)</li>
 *   <li>recommendedActions (deterministic)</li>
 *   <li>confidence score</li>
 *   <li>restrictions (what was excluded — PII, live asset locations, etc.)</li>
 *   <li>area-scope information</li>
 * </ul>
 *
 * Packets never include client-level addresses, live asset coordinates,
 * provider live locations, courier live locations, sensitive operational
 * routes, or other restricted layers unless Tshepo authorized the actor and
 * purpose explicitly (HIGHLY_RESTRICTED + EMERGENCY_RESPONSE).
 */
@Service
public class NdilaAiContextPacketService {

    private final NdilaSpatialIntelligenceService intelligence;
    private final NdilaTshepoGuard tshepoGuard;

    public NdilaAiContextPacketService(NdilaSpatialIntelligenceService intelligence,
                                       NdilaTshepoGuard tshepoGuard) {
        this.intelligence = intelligence;
        this.tshepoGuard = tshepoGuard;
    }

    public ContextPacket generate(NdilaOperationContext ctx, String question, AreaScope areaScope,
                                  boolean includeRecommendations) {
        NdilaTshepoGuard.Decision authz = tshepoGuard.authorize(ctx, "ai-context-packet");
        if (!authz.allowed()) {
            return ContextPacket.denied(authz.reason());
        }
        String district = areaScope == null ? null : areaScope.id();
        NdilaSpatialIntelligenceService.CoordinateQualityReport quality =
                intelligence.coordinateQuality(ctx.tenantId(), district);

        List<Map<String, Object>> signals = new ArrayList<>();
        if (quality.duplicateGroups() > 0) {
            signals.add(Map.of(
                    "type", "DUPLICATE_COORDINATES",
                    "count", quality.duplicateGroups(),
                    "severity", "MEDIUM"));
        }
        if (quality.unverified() > 0) {
            signals.add(Map.of(
                    "type", "UNVERIFIED_COORDINATES",
                    "count", quality.unverified(),
                    "severity", "LOW"));
        }
        if (quality.implausible() > 0) {
            signals.add(Map.of(
                    "type", "OUTSIDE_EXPECTED_BOUNDARY",
                    "count", quality.implausible(),
                    "severity", "HIGH"));
        }

        List<String> recommendations = new ArrayList<>();
        if (includeRecommendations) {
            if (quality.implausible() > 0) {
                recommendations.add("Assign field verification for facilities outside expected boundaries.");
            }
            if (quality.duplicateGroups() > 0) {
                recommendations.add("Review duplicate coordinates before national reporting.");
            }
            if (quality.unverified() > 0) {
                recommendations.add("Prioritize verification for high-volume facilities.");
            }
        }

        String summary = String.format(
                "There are %d locations in the selected scope with coordinate quality issues. "
                        + "%d have no coordinates, %d are unverified, %d appear implausible for the region, "
                        + "and %d coordinate-duplicate groups were detected.",
                quality.missing() + quality.unverified() + quality.implausible() + quality.duplicateGroups(),
                quality.missing(), quality.unverified(), quality.implausible(), quality.duplicateGroups());

        List<String> restrictions = List.of(
                "No client-level locations included.",
                "Live asset locations excluded.",
                "Outbreak-sensitive locations excluded.");

        String packetId = "ndila-ai-ctx-" + UUID.randomUUID();
        return new ContextPacket(true, null, packetId, summary, signals, recommendations,
                quality.confidence(), restrictions, areaScope, quality.explainability());
    }

    public record AreaScope(String level, String id) {}

    public record ContextPacket(
            boolean success,
            String denialReason,
            String contextPacketId,
            String summary,
            List<Map<String, Object>> signals,
            List<String> recommendedActions,
            double confidence,
            List<String> restrictions,
            AreaScope areaScope,
            List<String> explainability
    ) {
        public static ContextPacket denied(String reason) {
            return new ContextPacket(false, reason, null, null, List.of(), List.of(),
                    0.0, List.of(), null, List.of());
        }
    }
}
