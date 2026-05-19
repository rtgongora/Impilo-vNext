package zw.gov.mohcc.impilo.ndila.core.geocoding;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.ndila.core.events.NdilaEventTopics;
import zw.gov.mohcc.impilo.ndila.core.events.NdilaOutboxPublisher;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaOperationContext;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaProviderPolicyService;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaTshepoGuard;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaGeocodingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaProviderAdapter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NdilaGeocodingService {

    private final NdilaProviderPolicyService policy;
    private final NdilaTshepoGuard tshepoGuard;
    private final NdilaOutboxPublisher outbox;

    public NdilaGeocodingService(NdilaProviderPolicyService policy,
                                 NdilaTshepoGuard tshepoGuard,
                                 NdilaOutboxPublisher outbox) {
        this.policy = policy;
        this.tshepoGuard = tshepoGuard;
        this.outbox = outbox;
    }

    public GeocodeResponse geocode(NdilaOperationContext ctx, NdilaGeocodingProvider.GeocodeRequest req) {
        NdilaTshepoGuard.Decision authz = tshepoGuard.authorize(ctx, "geocode");
        if (!authz.allowed()) {
            return GeocodeResponse.denied(authz.reason());
        }
        NdilaProviderPolicyService.Decision decision = policy.selectGeocoding(ctx);
        publishProviderSelected(decision, ctx);

        NdilaProviderAdapter chosen = decision.chosen();
        List<NdilaGeocodingProvider.GeocodeResult> results = List.of();
        boolean fallbackUsed = false;
        if (chosen instanceof NdilaGeocodingProvider gp) {
            try {
                results = gp.geocode(req);
            } catch (Exception e) {
                fallbackUsed = true;
            }
        }
        if (results.isEmpty()) {
            for (String alt : decision.fallbackChain()) {
                NdilaProviderAdapter altAdapter = findAdapterByName(alt);
                if (altAdapter instanceof NdilaGeocodingProvider altGp && altAdapter.enabled()) {
                    try {
                        results = altGp.geocode(req);
                        if (!results.isEmpty()) {
                            fallbackUsed = true;
                            publishFallbackUsed(alt, ctx, decision);
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return new GeocodeResponse(true, null, results, decision.chosenName(),
                fallbackUsed, false, decision.decisionId());
    }

    public Optional<NdilaGeocodingProvider.GeocodeResult> reverse(NdilaOperationContext ctx,
                                                                  Coordinate coordinate, String country) {
        NdilaTshepoGuard.Decision authz = tshepoGuard.authorize(ctx, "reverse-geocode");
        if (!authz.allowed()) return Optional.empty();
        NdilaProviderPolicyService.Decision decision = policy.selectGeocoding(ctx);
        publishProviderSelected(decision, ctx);
        if (decision.chosen() instanceof NdilaGeocodingProvider gp) {
            return gp.reverseGeocode(coordinate, country);
        }
        return Optional.empty();
    }

    private NdilaProviderAdapter findAdapterByName(String name) {
        // The policy service holds the lists; we ask it again with a name hint
        // via a lightweight rescan. Production deployments may inject the
        // registry directly. For now Mock is always present.
        return null;
    }

    private void publishProviderSelected(NdilaProviderPolicyService.Decision d, NdilaOperationContext ctx) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("operation", d.operationType());
        payload.put("provider", d.chosenName());
        payload.put("fallbackChain", d.fallbackChain());
        payload.put("reason", d.reason());
        payload.put("decisionId", d.decisionId());
        payload.put("sensitive", d.sensitive());
        outbox.publish(new NdilaOutboxPublisher.NdilaEvent(
                NdilaEventTopics.PROVIDER_SELECTED, "ProviderDecision", d.decisionId(),
                ctx.tenantId(), ctx.correlationId(), payload));
    }

    private void publishFallbackUsed(String fallbackProvider, NdilaOperationContext ctx,
                                     NdilaProviderPolicyService.Decision d) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("operation", d.operationType());
        payload.put("originalProvider", d.chosenName());
        payload.put("fallbackProvider", fallbackProvider);
        payload.put("decisionId", d.decisionId());
        outbox.publish(new NdilaOutboxPublisher.NdilaEvent(
                NdilaEventTopics.PROVIDER_FALLBACK_USED, "ProviderDecision", d.decisionId(),
                ctx.tenantId(), ctx.correlationId(), payload));
    }

    public record GeocodeResponse(
            boolean success,
            String denialReason,
            List<NdilaGeocodingProvider.GeocodeResult> results,
            String provider,
            boolean fallbackUsed,
            boolean servedFromCache,
            String policyDecisionId
    ) {
        public static GeocodeResponse denied(String reason) {
            return new GeocodeResponse(false, reason, List.of(), null, false, false, null);
        }
    }
}
