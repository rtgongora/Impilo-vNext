package zw.gov.mohcc.impilo.mushex.service.rail;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterReadinessStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes a read-only readiness view of every {@link AdapterType} for operator
 * surfaces. Deterministic; reads only {@link AdapterRegistry} presence and
 * {@link MushexProperties} configuration. Never contacts a payment provider and
 * never reads credentials.
 *
 * <p>The readiness model is intentionally conservative — see
 * {@code docs/design/phase-2-adapter-readiness.md} for the state-machine and the
 * production-default posture. New states (e.g. {@code DEGRADED}) are reserved
 * for a future stage where real adapters surface a runtime health signal.
 */
@Service
public class AdapterReadinessService {

    private final AdapterRegistry adapterRegistry;
    private final MushexProperties properties;

    public AdapterReadinessService(AdapterRegistry adapterRegistry, MushexProperties properties) {
        this.adapterRegistry = adapterRegistry;
        this.properties = properties;
    }

    /**
     * Compute a readiness snapshot for every {@link AdapterType} in stable
     * enum-declaration order. Always returns a fully populated list — adapters
     * that are not registered are reported as {@link AdapterReadinessStatus#NOT_REGISTERED}
     * rather than omitted, so operator dashboards can show the complete inventory.
     */
    public List<AdapterReadiness> describeAll() {
        List<AdapterReadiness> out = new ArrayList<>(AdapterType.values().length);
        for (AdapterType type : AdapterType.values()) {
            out.add(describe(type));
        }
        return List.copyOf(out);
    }

    /**
     * Compute readiness for a single rail. Exposed for testability and for
     * targeted operator drill-down views.
     */
    public AdapterReadiness describe(AdapterType type) {
        if (!adapterRegistry.has(type)) {
            return new AdapterReadiness(type, AdapterReadinessStatus.NOT_REGISTERED,
                    false, false,
                    "No PaymentRailAdapter bean registered for " + type);
        }
        if (type == AdapterType.SANDBOX) {
            if (properties.getAdapters().getSandbox().isEnabled()) {
                return new AdapterReadiness(type, AdapterReadinessStatus.READY_SANDBOX,
                        false, true,
                        "Sandbox adapter is enabled; suitable for non-production simulation only");
            }
            return new AdapterReadiness(type, AdapterReadinessStatus.DISABLED,
                    false, false,
                    "Sandbox adapter is disabled by mushex.adapters.sandbox.enabled=false");
        }
        MushexProperties.RealRail cfg = realRailConfig(type);
        if (cfg == null || !cfg.isEnabled()) {
            return new AdapterReadiness(type, AdapterReadinessStatus.DISABLED,
                    false, false,
                    "Real-money rail " + type + " is disabled. Operator must set "
                            + propertyPath(type) + ".enabled=true to enable.");
        }
        if (!cfg.isCredentialsConfigured()) {
            return new AdapterReadiness(type, AdapterReadinessStatus.CREDENTIALS_MISSING,
                    false, false,
                    "Real-money rail " + type + " is enabled but credentials are not declared as configured. "
                            + "Set " + propertyPath(type)
                            + ".credentialsConfigured=true once secret-management wiring is verified.");
        }
        // Configuration alone must never claim readiness the attempt-time safety gate
        // would contradict: a stub adapter (liveCapable() == false) is hard-blocked at
        // attempt time, so reporting READY_LIVE here would mislead the operator.
        if (!adapterRegistry.getAdapter(type).liveCapable()) {
            return new AdapterReadiness(type, AdapterReadinessStatus.STUB_NOT_LIVE_CAPABLE,
                    false, false,
                    "Real-money rail " + type + " is enabled and credentials are declared, but the adapter "
                            + "implementation is a stub (liveCapable=false) and cannot move money. The "
                            + "attempt-time safety gate blocks this rail.");
        }
        return new AdapterReadiness(type, AdapterReadinessStatus.READY_LIVE,
                true, false,
                "Real-money rail " + type + " is enabled, operator declares credentials are configured, "
                        + "and the adapter implementation reports live capability.");
    }

    private MushexProperties.RealRail realRailConfig(AdapterType type) {
        MushexProperties.Adapters adapters = properties.getAdapters();
        return switch (type) {
            case MOBILE_MONEY -> adapters.getMobileMoney();
            case BANK_TRANSFER -> adapters.getBankTransfer();
            case CARD_GATEWAY -> adapters.getCardGateway();
            case SANDBOX -> null;
        };
    }

    private String propertyPath(AdapterType type) {
        return switch (type) {
            case MOBILE_MONEY -> "mushex.adapters.mobile-money";
            case BANK_TRANSFER -> "mushex.adapters.bank-transfer";
            case CARD_GATEWAY -> "mushex.adapters.card-gateway";
            case SANDBOX -> "mushex.adapters.sandbox";
        };
    }
}
