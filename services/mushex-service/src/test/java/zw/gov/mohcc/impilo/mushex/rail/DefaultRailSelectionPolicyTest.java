package zw.gov.mohcc.impilo.mushex.rail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.RailSelectionReason;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterRegistry;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterResponse;
import zw.gov.mohcc.impilo.mushex.service.adapter.PaymentRailAdapter;
import zw.gov.mohcc.impilo.mushex.service.rail.DefaultRailSelectionPolicy;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionException;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionRequest;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DefaultRailSelectionPolicy}. Runs without Spring; constructs a
 * real {@link AdapterRegistry} with a controllable set of stub adapters per case.
 *
 * <p>Covers cases §13.1–§13.9 of {@code docs/design/g4-rail-selection-policy.md}.
 * The idempotency-replay case (§13.10) and outbox-payload case (§13.11) are integration
 * concerns and live in {@code PaymentIntentServiceTest} once step 2 of the
 * implementation is in place.
 */
class DefaultRailSelectionPolicyTest {

    private MushexProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MushexProperties();
    }

    // ----- helpers ------------------------------------------------------------------

    private static PaymentRailAdapter stub(AdapterType type) {
        return new PaymentRailAdapter() {
            @Override public String adapterType() { return type.name(); }
            @Override public AdapterResponse initiatePayment(String i, BigDecimal a, String c, Map<String, String> cfg) {
                return new AdapterResponse("ref", "PENDING", "", Map.of());
            }
            @Override public AdapterResponse checkStatus(String r, Map<String, String> cfg) {
                return new AdapterResponse("ref", "PENDING", "", Map.of());
            }
            @Override public boolean verifyWebhook(String s, String p, Map<String, String> cfg) { return true; }
            @Override public AdapterResponse initiateRefund(String r, BigDecimal a, Map<String, String> cfg) {
                return new AdapterResponse("ref", "PENDING", "", Map.of());
            }
        };
    }

    private AdapterRegistry registryWith(AdapterType... types) {
        List<PaymentRailAdapter> adapters = new ArrayList<>();
        for (AdapterType t : types) {
            adapters.add(stub(t));
        }
        return new AdapterRegistry(adapters);
    }

    private RailSelectionRequest req(String preferred, Boolean allowFallback, Boolean directGateway) {
        return new RailSelectionRequest(
                preferred,
                allowFallback,
                directGateway,
                SourceType.COSTA_BILL,
                new BigDecimal("100.00"),
                "USD",
                UUID.randomUUID(),
                false);
    }

    // ----- cases --------------------------------------------------------------------

    @Test
    void explicitPreferredRailRegistered_isUsedDirectly() {
        var registry = registryWith(AdapterType.SANDBOX, AdapterType.MOBILE_MONEY);
        var policy = new DefaultRailSelectionPolicy(registry, properties);

        RailSelectionResult result = policy.select(req("MOBILE_MONEY", null, null));

        assertEquals(AdapterType.MOBILE_MONEY, result.effectiveRail());
        assertEquals(AdapterType.MOBILE_MONEY, result.preferredRail());
        assertFalse(result.fallbackApplied());
        assertEquals(RailSelectionReason.EXPLICIT_PREFERRED, result.reason());
        assertNotNull(result.reasonDetail());
    }

    @Test
    void explicitPreferredRailUnregistered_fallbackAllowed_demotesToSandbox() {
        var registry = registryWith(AdapterType.SANDBOX);
        var policy = new DefaultRailSelectionPolicy(registry, properties);

        RailSelectionResult result = policy.select(req("MOBILE_MONEY", true, null));

        assertEquals(AdapterType.SANDBOX, result.effectiveRail());
        assertEquals(AdapterType.MOBILE_MONEY, result.preferredRail());
        assertTrue(result.fallbackApplied());
        assertEquals(RailSelectionReason.PREFERRED_UNAVAILABLE_FALLBACK, result.reason());
    }

    @Test
    void explicitPreferredRailUnregistered_fallbackDisallowedByCaller_throws() {
        var registry = registryWith(AdapterType.SANDBOX);
        var policy = new DefaultRailSelectionPolicy(registry, properties);

        var ex = assertThrows(RailSelectionException.class,
                () -> policy.select(req("MOBILE_MONEY", false, null)));

        assertEquals("RAIL_ADAPTER_UNAVAILABLE", ex.getErrorCode());
        assertEquals(RailSelectionReason.REJECTED_UNAVAILABLE_NO_FALLBACK, ex.getReason());
    }

    @Test
    void explicitPreferredRailUnregistered_fallbackDisallowedByDeployment_throws() {
        properties.getRailSelection().setAllowSandboxFallback(false);
        var registry = registryWith(AdapterType.SANDBOX);
        var policy = new DefaultRailSelectionPolicy(registry, properties);

        var ex = assertThrows(RailSelectionException.class,
                () -> policy.select(req("CARD_GATEWAY", true, null)));

        assertEquals("RAIL_ADAPTER_UNAVAILABLE", ex.getErrorCode());
    }

    @Test
    void unknownRailString_throwsWithStableCode() {
        var registry = registryWith(AdapterType.SANDBOX);
        var policy = new DefaultRailSelectionPolicy(registry, properties);

        var ex = assertThrows(RailSelectionException.class,
                () -> policy.select(req("BITCOIN", null, null)));

        assertEquals("RAIL_ADAPTER_UNKNOWN", ex.getErrorCode());
        assertEquals(RailSelectionReason.REJECTED_UNKNOWN_RAIL, ex.getReason());
    }

    @Test
    void noPreference_usesConfiguredDefaultRail() {
        var registry = registryWith(AdapterType.SANDBOX, AdapterType.MOBILE_MONEY);
        var policy = new DefaultRailSelectionPolicy(registry, properties);

        RailSelectionResult result = policy.select(req(null, null, null));

        assertEquals(AdapterType.SANDBOX, result.effectiveRail());
        assertNull(result.preferredRail());
        assertFalse(result.fallbackApplied());
        assertFalse(result.directGatewayRequested());
        assertEquals(RailSelectionReason.DEFAULT_NO_PREFERENCE, result.reason());
    }

    @Test
    void directGatewayAllowedByDeployment_picksFirstNonSandboxRail() {
        properties.getRailSelection().setAllowDirectGateway(true);
        var registry = registryWith(AdapterType.SANDBOX, AdapterType.BANK_TRANSFER, AdapterType.MOBILE_MONEY);
        var policy = new DefaultRailSelectionPolicy(registry, properties);

        RailSelectionResult result = policy.select(req(null, null, true));

        assertEquals(AdapterType.BANK_TRANSFER, result.effectiveRail(),
                "CARD_GATEWAY not registered, BANK_TRANSFER comes next in deterministic order");
        assertTrue(result.directGatewayRequested());
        assertEquals(RailSelectionReason.DIRECT_GATEWAY_REQUESTED, result.reason());
    }

    @Test
    void directGatewayDisallowedByDeployment_silentlyDemotesToDefault() {
        // production default is allowDirectGateway=false
        var registry = registryWith(AdapterType.SANDBOX, AdapterType.CARD_GATEWAY);
        var policy = new DefaultRailSelectionPolicy(registry, properties);

        RailSelectionResult result = policy.select(req(null, null, true));

        assertEquals(AdapterType.SANDBOX, result.effectiveRail());
        assertFalse(result.directGatewayRequested(),
                "directGatewayRequested must be clamped to false when policy disallows it");
        assertEquals(RailSelectionReason.DEFAULT_NO_PREFERENCE, result.reason());
    }

    @Test
    void directGatewayAllowedButNoExternalRailRegistered_demotesToDefault() {
        properties.getRailSelection().setAllowDirectGateway(true);
        var registry = registryWith(AdapterType.SANDBOX);
        var policy = new DefaultRailSelectionPolicy(registry, properties);

        RailSelectionResult result = policy.select(req(null, null, true));

        assertEquals(AdapterType.SANDBOX, result.effectiveRail());
        assertEquals(RailSelectionReason.DEFAULT_NO_PREFERENCE, result.reason());
    }

    @Test
    void safetySwitchOff_forcesSandboxRegardlessOfPreference() {
        properties.getRailSelection().setEnabled(false);
        var registry = registryWith(AdapterType.SANDBOX, AdapterType.MOBILE_MONEY);
        var policy = new DefaultRailSelectionPolicy(registry, properties);

        RailSelectionResult result = policy.select(req("MOBILE_MONEY", null, true));

        assertEquals(AdapterType.SANDBOX, result.effectiveRail());
        assertEquals(RailSelectionReason.SAFETY_SWITCH_FORCED_SANDBOX, result.reason());
    }

    @Test
    void impiloSimulationFlag_forcesSandboxRegardlessOfPreference() {
        var registry = registryWith(AdapterType.SANDBOX, AdapterType.MOBILE_MONEY);
        var policy = new DefaultRailSelectionPolicy(registry, properties);

        RailSelectionRequest simRequest = new RailSelectionRequest(
                "MOBILE_MONEY", null, true,
                SourceType.COSTA_BILL, new BigDecimal("100.00"), "USD", UUID.randomUUID(),
                true /* impiloSimulation */);

        RailSelectionResult result = policy.select(simRequest);

        assertEquals(AdapterType.SANDBOX, result.effectiveRail());
        assertEquals(RailSelectionReason.SAFETY_SWITCH_FORCED_SANDBOX, result.reason());
    }

    @Test
    void preferredRailIsCaseInsensitive() {
        var registry = registryWith(AdapterType.SANDBOX, AdapterType.MOBILE_MONEY);
        var policy = new DefaultRailSelectionPolicy(registry, properties);

        RailSelectionResult result = policy.select(req("  mobile_money  ", null, null));

        assertEquals(AdapterType.MOBILE_MONEY, result.effectiveRail());
        assertEquals(RailSelectionReason.EXPLICIT_PREFERRED, result.reason());
    }

    @Test
    void emptyRequestFactory_producesDefaultNoPreferenceResult() {
        var registry = registryWith(AdapterType.SANDBOX);
        var policy = new DefaultRailSelectionPolicy(registry, properties);

        RailSelectionResult result = policy.select(RailSelectionRequest.empty(
                SourceType.COSTA_BILL, new BigDecimal("100.00"), "USD", UUID.randomUUID(), false));

        assertEquals(AdapterType.SANDBOX, result.effectiveRail());
        assertEquals(RailSelectionReason.DEFAULT_NO_PREFERENCE, result.reason());
    }
}
