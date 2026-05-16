package zw.gov.mohcc.impilo.ndila.core.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaGeocodingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaProviderAdapter;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaRoutingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaTileProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.ProviderCapabilities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Policy-driven provider selection.
 *
 * <p>Doctrine:
 * <ul>
 *   <li>Default provider stack is open + sovereign-friendly (Mock + self-hosted
 *       OSM/OSRM/Nominatim/Pelias).</li>
 *   <li>Commercial providers (Mapbox, Google Maps, HERE, Azure) are optional
 *       fallbacks, never the default.</li>
 *   <li>Sensitive workflows (RESTRICTED, HIGHLY_RESTRICTED) prefer
 *       sovereign / internal providers and refuse commercial ones unless
 *       explicitly authorised.</li>
 *   <li>Production tile rendering refuses public OSM tile servers.</li>
 *   <li>Provider selection is auditable: every decision returns a
 *       {@link Decision} that includes the chosen provider, the alternates
 *       ordered as the fallback chain, the policy reason, and a decision ID.</li>
 * </ul>
 */
@Service
public class NdilaProviderPolicyService {

    private static final Logger log = LoggerFactory.getLogger(NdilaProviderPolicyService.class);

    private final List<NdilaGeocodingProvider> geocoders;
    private final List<NdilaRoutingProvider> routers;
    private final List<NdilaTileProvider> tiles;
    private final String defaultGeocoding;
    private final String defaultRouting;
    private final String defaultTiles;
    private final boolean preferInternalForSensitive;
    private final boolean blockPublicOsmInProduction;

    public NdilaProviderPolicyService(
            List<NdilaGeocodingProvider> geocoders,
            List<NdilaRoutingProvider> routers,
            List<NdilaTileProvider> tiles,
            @Value("${ndila.providers.default-geocoding:MOCK}") String defaultGeocoding,
            @Value("${ndila.providers.default-routing:MOCK}") String defaultRouting,
            @Value("${ndila.providers.default-tiles:MOCK}") String defaultTiles,
            @Value("${ndila.sovereignty.prefer-internal-for-sensitive:true}") boolean preferInternalForSensitive,
            @Value("${ndila.sovereignty.block-public-osm-tiles-in-production:true}") boolean blockPublicOsmInProduction) {
        this.geocoders = geocoders;
        this.routers = routers;
        this.tiles = tiles;
        this.defaultGeocoding = defaultGeocoding;
        this.defaultRouting = defaultRouting;
        this.defaultTiles = defaultTiles;
        this.preferInternalForSensitive = preferInternalForSensitive;
        this.blockPublicOsmInProduction = blockPublicOsmInProduction;
    }

    public Decision selectGeocoding(NdilaOperationContext ctx) {
        return select(ctx, "GEOCODING", defaultGeocoding,
                geocoders.stream().map(p -> (NdilaProviderAdapter) p).toList());
    }

    public Decision selectRouting(NdilaOperationContext ctx) {
        return select(ctx, "ROUTING", defaultRouting,
                routers.stream().map(p -> (NdilaProviderAdapter) p).toList());
    }

    public Decision selectTiles(NdilaOperationContext ctx) {
        List<NdilaProviderAdapter> productionSafeTiles = tiles.stream()
                .filter(t -> !"production".equalsIgnoreCase(ctx.environment())
                        || !blockPublicOsmInProduction || t.productionSafe())
                .map(p -> (NdilaProviderAdapter) p)
                .toList();
        return select(ctx, "TILES", defaultTiles, productionSafeTiles);
    }

    private Decision select(NdilaOperationContext ctx, String operationType, String preferred,
                            List<NdilaProviderAdapter> candidates) {
        // 1. Filter to enabled providers.
        List<NdilaProviderAdapter> enabled = candidates.stream()
                .filter(NdilaProviderAdapter::enabled)
                .collect(Collectors.toList());

        // 2. Sensitivity gate — for restricted workflows prefer sovereign /
        //    internal providers and exclude commercial ones unless explicitly
        //    authorised (Tshepo path; not implemented here, must be set by the
        //    caller as part of the operation context via purposeOfUse).
        boolean sensitive = ctx.sensitivity().atLeast(NdilaSensitivity.RESTRICTED);
        if (sensitive && preferInternalForSensitive) {
            enabled = enabled.stream()
                    .filter(p -> p.capabilities().supportsSensitiveWorkflows()
                            || "MOCK".equals(p.providerName())
                            || p.providerName().startsWith("OSM")
                            || "ZW_GIS".equals(p.providerName())
                            || "OFFLINE_TILES".equals(p.providerName()))
                    .collect(Collectors.toList());
        }

        // 3. Offline-mode gate.
        if (ctx.offlineMode()) {
            enabled = enabled.stream()
                    .filter(p -> p.capabilities().supportsOffline() || "MOCK".equals(p.providerName()))
                    .collect(Collectors.toList());
        }

        // 4. Order: preferred-default first, then sovereign / cheaper / lower-residency providers, then commercial.
        Comparator<NdilaProviderAdapter> order = Comparator
                .comparingInt((NdilaProviderAdapter p) -> p.providerName().equals(preferred) ? 0 : 1)
                .thenComparingInt(p -> sensitive && p.capabilities().supportsSensitiveWorkflows() ? 0 : 1)
                .thenComparingInt(p -> costRank(p.capabilities().estimatedCostCategory()))
                .thenComparing(NdilaProviderAdapter::providerName);
        List<NdilaProviderAdapter> ordered = enabled.stream().sorted(order).toList();

        // 5. Fallback safety — Mock provider is always last-resort.
        if (ordered.isEmpty()) {
            Optional<NdilaProviderAdapter> mock = candidates.stream()
                    .filter(p -> "MOCK".equals(p.providerName()))
                    .findFirst();
            ordered = mock.map(List::of).orElse(List.of());
        }

        if (ordered.isEmpty()) {
            // Truly nothing available — caller must handle.
            log.warn("Ndila provider policy: no providers available for operation={} tenant={} sensitivity={}",
                    operationType, ctx.tenantId(), ctx.sensitivity());
            return new Decision(operationType, null, List.of(), "NO_PROVIDERS_AVAILABLE",
                    UUID.randomUUID().toString(), sensitive);
        }

        String decisionId = "ndila-policy-" + UUID.randomUUID();
        String reason = decisionReason(preferred, ordered.get(0).providerName(), sensitive, ctx.offlineMode());
        List<String> fallbackChain = ordered.stream().map(NdilaProviderAdapter::providerName).toList();
        return new Decision(operationType, ordered.get(0), fallbackChain, reason, decisionId, sensitive);
    }

    private static int costRank(String c) {
        if (c == null) return 5;
        return switch (c) {
            case "FREE" -> 0;
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            default -> 5;
        };
    }

    private static String decisionReason(String preferred, String chosen, boolean sensitive, boolean offline) {
        List<String> reasons = new ArrayList<>();
        if (sensitive) reasons.add("sensitive-workflow");
        if (offline) reasons.add("offline-mode");
        if (preferred.equals(chosen)) reasons.add("preferred-default");
        else reasons.add("fallback-by-availability");
        return String.join(",", reasons);
    }

    public record Decision(
            String operationType,
            NdilaProviderAdapter chosen,
            List<String> fallbackChain,
            String reason,
            String decisionId,
            boolean sensitive
    ) {
        public String chosenName() { return chosen == null ? "NONE" : chosen.providerName(); }
        public boolean usedFallback() {
            return chosen != null && !fallbackChain.isEmpty()
                    && !fallbackChain.get(0).equals(chosen.providerName());
        }
        public ProviderCapabilities capabilities() {
            return chosen == null ? null : chosen.capabilities();
        }
    }
}
