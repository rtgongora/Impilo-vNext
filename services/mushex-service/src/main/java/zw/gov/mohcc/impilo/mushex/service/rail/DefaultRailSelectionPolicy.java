package zw.gov.mohcc.impilo.mushex.service.rail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.RailSelectionReason;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterRegistry;

import java.util.List;
import java.util.Locale;

/**
 * Default deterministic {@link RailSelectionPolicy}.
 *
 * <p>The algorithm matches the pseudocode in §6 of the G-4 design doc
 * ({@code docs/design/g4-rail-selection-policy.md}). Key invariants:
 *
 * <ul>
 *   <li>Same inputs always produce the same result (no time-, registry-state-, or
 *       random-source-dependent branches beyond which adapters are registered).</li>
 *   <li>Never auto-selects a non-SANDBOX rail unless either (a) the caller explicitly
 *       preferences it, or (b) the caller opts into direct-gateway mode and the
 *       deployment has enabled it.</li>
 *   <li>The {@code mushex.rail-selection.enabled} safety switch and an Impilo simulation
 *       flag both force SANDBOX regardless of preference.</li>
 * </ul>
 */
@Component
public class DefaultRailSelectionPolicy implements RailSelectionPolicy {

    private static final Logger log = LoggerFactory.getLogger(DefaultRailSelectionPolicy.class);

    /**
     * Stable, enum-declaration-order list of non-SANDBOX rails consulted when a caller
     * requests direct-gateway mode without preferring a specific adapter.
     */
    private static final List<AdapterType> DIRECT_GATEWAY_CANDIDATES = List.of(
            AdapterType.CARD_GATEWAY,
            AdapterType.BANK_TRANSFER,
            AdapterType.MOBILE_MONEY);

    private final AdapterRegistry adapterRegistry;
    private final MushexProperties properties;

    public DefaultRailSelectionPolicy(AdapterRegistry adapterRegistry, MushexProperties properties) {
        this.adapterRegistry = adapterRegistry;
        this.properties = properties;
    }

    @Override
    public RailSelectionResult select(RailSelectionRequest request) {
        MushexProperties.RailSelection cfg = properties.getRailSelection();

        if (!cfg.isEnabled()) {
            return forcedSandbox("rail-selection safety switch is disabled");
        }
        if (request.impiloSimulation()) {
            return forcedSandbox("impilo_simulation metadata present; cannot route to a real rail");
        }

        boolean directGatewayRequested = Boolean.TRUE.equals(request.directGatewayAllowed());
        if (directGatewayRequested && !cfg.isAllowDirectGateway()) {
            log.info("Caller requested directGatewayAllowed=true but deployment policy disallows it; demoting to default rail");
            directGatewayRequested = false;
        }

        AdapterType preferred = parsePreferredOrThrow(request.preferredRailAdapter());

        if (preferred != null && adapterRegistry.has(preferred)) {
            return new RailSelectionResult(
                    preferred,
                    preferred,
                    false,
                    directGatewayRequested,
                    RailSelectionReason.EXPLICIT_PREFERRED,
                    "Preferred rail " + preferred + " is registered and was selected");
        }

        if (preferred != null) {
            boolean callerAllowsFallback = !Boolean.FALSE.equals(request.allowFallback());
            if (callerAllowsFallback && cfg.isAllowSandboxFallback()) {
                return new RailSelectionResult(
                        AdapterType.SANDBOX,
                        preferred,
                        true,
                        directGatewayRequested,
                        RailSelectionReason.PREFERRED_UNAVAILABLE_FALLBACK,
                        "Preferred rail " + preferred + " is not registered in this environment; "
                                + "fell back to SANDBOX");
            }
            throw new RailSelectionException(
                    "RAIL_ADAPTER_UNAVAILABLE",
                    RailSelectionReason.REJECTED_UNAVAILABLE_NO_FALLBACK,
                    "No payment rail adapter is registered for preferred type " + preferred
                            + " and fallback is not allowed");
        }

        if (directGatewayRequested) {
            for (AdapterType candidate : DIRECT_GATEWAY_CANDIDATES) {
                if (adapterRegistry.has(candidate)) {
                    return new RailSelectionResult(
                            candidate,
                            null,
                            false,
                            true,
                            RailSelectionReason.DIRECT_GATEWAY_REQUESTED,
                            "Direct-gateway mode permitted; selected first registered non-SANDBOX rail "
                                    + candidate);
                }
            }
            log.info("Direct-gateway requested but no non-SANDBOX adapter is registered; using default rail");
        }

        AdapterType defaultRail = cfg.getDefaultRail() != null ? cfg.getDefaultRail() : AdapterType.SANDBOX;
        return new RailSelectionResult(
                defaultRail,
                null,
                false,
                directGatewayRequested,
                RailSelectionReason.DEFAULT_NO_PREFERENCE,
                "No preferred rail supplied; selected configured default rail " + defaultRail);
    }

    private AdapterType parsePreferredOrThrow(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AdapterType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new RailSelectionException(
                    "RAIL_ADAPTER_UNKNOWN",
                    RailSelectionReason.REJECTED_UNKNOWN_RAIL,
                    "Unknown payment rail adapter: " + raw);
        }
    }

    private RailSelectionResult forcedSandbox(String detail) {
        return new RailSelectionResult(
                AdapterType.SANDBOX,
                null,
                false,
                false,
                RailSelectionReason.SAFETY_SWITCH_FORCED_SANDBOX,
                detail);
    }
}
