package zw.gov.mohcc.impilo.mushex.rail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.RailEnforcementReason;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterRegistry;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterResponse;
import zw.gov.mohcc.impilo.mushex.service.adapter.PaymentRailAdapter;
import zw.gov.mohcc.impilo.mushex.service.rail.RailEnforcement;
import zw.gov.mohcc.impilo.mushex.service.rail.RailEnforcementException;
import zw.gov.mohcc.impilo.mushex.service.rail.RailEnforcementOutcome;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RailEnforcement}. Covers the test matrix from
 * {@code docs/design/phase-3-attempt-time-rail-enforcement-implementation.md} §9.
 */
class RailEnforcementTest {

    private RailEnforcement enforcement;
    private MushexProperties.RailSelection config;

    @BeforeEach
    void setUp() {
        enforcement = new RailEnforcement(new ObjectMapper());
        config = new MushexProperties().getRailSelection();
    }

    private static PaymentRailAdapter stub(AdapterType type) {
        return new PaymentRailAdapter() {
            @Override public String adapterType() { return type.name(); }
            @Override public AdapterResponse initiatePayment(String i, BigDecimal a, String c, Map<String, String> cfg) {
                return new AdapterResponse("r", "PENDING", "", Map.of());
            }
            @Override public AdapterResponse checkStatus(String r, Map<String, String> cfg) {
                return new AdapterResponse("r", "PENDING", "", Map.of());
            }
            @Override public boolean verifyWebhook(String s, String p, Map<String, String> cfg) { return true; }
            @Override public AdapterResponse initiateRefund(String r, BigDecimal a, Map<String, String> cfg) {
                return new AdapterResponse("r", "PENDING", "", Map.of());
            }
        };
    }

    private AdapterRegistry registryWith(AdapterType... types) {
        List<PaymentRailAdapter> list = new ArrayList<>();
        for (AdapterType t : types) list.add(stub(t));
        return new AdapterRegistry(list);
    }

    private PaymentIntentEntity intentWithMetadata(String json) {
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setIntentId("01TEST");
        intent.setMetadata(json);
        return intent;
    }

    @Test
    void explicitMetadataWithRegisteredRailIsHonoured() {
        PaymentIntentEntity intent = intentWithMetadata("""
                {
                  "rail_selection": {
                    "effective_rail": "MOBILE_MONEY",
                    "preferred_rail": "MOBILE_MONEY",
                    "selection_version": "1"
                  }
                }""");
        AdapterRegistry registry = registryWith(AdapterType.SANDBOX, AdapterType.MOBILE_MONEY);

        RailEnforcementOutcome o = enforcement.resolve(intent, config, registry);

        assertEquals(AdapterType.MOBILE_MONEY, o.effectiveRail());
        assertEquals(AdapterType.MOBILE_MONEY, o.preferredRail());
        assertEquals(RailEnforcementReason.EXPLICIT_METADATA, o.reason());
        assertFalse(o.legacyIntent());
        assertEquals("1", o.selectionVersion());
    }

    @Test
    void legacyIntentWithoutMetadataFallsBackToConfigDefault() {
        PaymentIntentEntity intent = intentWithMetadata(null);
        AdapterRegistry registry = registryWith(AdapterType.SANDBOX);

        RailEnforcementOutcome o = enforcement.resolve(intent, config, registry);

        assertEquals(AdapterType.SANDBOX, o.effectiveRail());
        assertNull(o.preferredRail());
        assertEquals(RailEnforcementReason.LEGACY_FALLBACK, o.reason());
        assertTrue(o.legacyIntent());
        assertNull(o.selectionVersion());
    }

    @Test
    void emptyMetadataFallsBackToConfigDefault() {
        PaymentIntentEntity intent = intentWithMetadata("");
        AdapterRegistry registry = registryWith(AdapterType.SANDBOX);

        RailEnforcementOutcome o = enforcement.resolve(intent, config, registry);

        assertEquals(AdapterType.SANDBOX, o.effectiveRail());
        assertEquals(RailEnforcementReason.LEGACY_FALLBACK, o.reason());
    }

    @Test
    void impiloSimulationForcesSandboxRegardlessOfMetadata() {
        PaymentIntentEntity intent = intentWithMetadata("""
                {
                  "impilo_simulation": true,
                  "rail_selection": {
                    "effective_rail": "MOBILE_MONEY",
                    "preferred_rail": "MOBILE_MONEY"
                  }
                }""");
        AdapterRegistry registry = registryWith(AdapterType.SANDBOX, AdapterType.MOBILE_MONEY);

        RailEnforcementOutcome o = enforcement.resolve(intent, config, registry);

        assertEquals(AdapterType.SANDBOX, o.effectiveRail());
        assertEquals(AdapterType.MOBILE_MONEY, o.preferredRail());
        assertEquals(RailEnforcementReason.SIMULATION_LOCK, o.reason());
        assertFalse(o.legacyIntent());
    }

    @Test
    void impiloSimulationCamelCaseIsAlsoHonoured() {
        PaymentIntentEntity intent = intentWithMetadata("""
                { "impiloSimulation": true }""");
        AdapterRegistry registry = registryWith(AdapterType.SANDBOX);

        RailEnforcementOutcome o = enforcement.resolve(intent, config, registry);

        assertEquals(AdapterType.SANDBOX, o.effectiveRail());
        assertEquals(RailEnforcementReason.SIMULATION_LOCK, o.reason());
    }

    @Test
    void unknownEffectiveRailEnumThrows() {
        PaymentIntentEntity intent = intentWithMetadata("""
                { "rail_selection": { "effective_rail": "ALIEN_RAIL" } }""");
        AdapterRegistry registry = registryWith(AdapterType.SANDBOX);

        RailEnforcementException ex = assertThrows(RailEnforcementException.class,
                () -> enforcement.resolve(intent, config, registry));
        assertEquals(RailEnforcementException.CODE_RAIL_UNAVAILABLE, ex.getErrorCode());
    }

    @Test
    void knownEnumButUnregisteredAdapterThrows() {
        PaymentIntentEntity intent = intentWithMetadata("""
                { "rail_selection": { "effective_rail": "MOBILE_MONEY" } }""");
        AdapterRegistry registry = registryWith(AdapterType.SANDBOX);

        RailEnforcementException ex = assertThrows(RailEnforcementException.class,
                () -> enforcement.resolve(intent, config, registry));
        assertEquals(RailEnforcementException.CODE_RAIL_UNAVAILABLE, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("MOBILE_MONEY"));
    }

    @Test
    void blankEffectiveRailFallsBackAsLegacy() {
        PaymentIntentEntity intent = intentWithMetadata("""
                { "rail_selection": { "effective_rail": "" } }""");
        AdapterRegistry registry = registryWith(AdapterType.SANDBOX);

        RailEnforcementOutcome o = enforcement.resolve(intent, config, registry);

        assertEquals(AdapterType.SANDBOX, o.effectiveRail());
        assertEquals(RailEnforcementReason.LEGACY_FALLBACK, o.reason());
        assertTrue(o.legacyIntent());
    }

    @Test
    void railSelectionIsNotAnObjectFallsBackAsLegacy() {
        PaymentIntentEntity intent = intentWithMetadata("""
                { "rail_selection": "not-an-object" }""");
        AdapterRegistry registry = registryWith(AdapterType.SANDBOX);

        RailEnforcementOutcome o = enforcement.resolve(intent, config, registry);

        assertEquals(AdapterType.SANDBOX, o.effectiveRail());
        assertEquals(RailEnforcementReason.LEGACY_FALLBACK, o.reason());
    }

    @Test
    void invalidJsonMetadataFallsBackAsLegacy() {
        PaymentIntentEntity intent = intentWithMetadata("not-json");
        AdapterRegistry registry = registryWith(AdapterType.SANDBOX);

        RailEnforcementOutcome o = enforcement.resolve(intent, config, registry);

        assertEquals(AdapterType.SANDBOX, o.effectiveRail());
        assertEquals(RailEnforcementReason.LEGACY_FALLBACK, o.reason());
    }

    @Test
    void defaultRailNotRegisteredThrows() {
        PaymentIntentEntity intent = intentWithMetadata(null);
        AdapterRegistry registry = registryWith(AdapterType.MOBILE_MONEY);

        RailEnforcementException ex = assertThrows(RailEnforcementException.class,
                () -> enforcement.resolve(intent, config, registry));
        assertEquals(RailEnforcementException.CODE_RAIL_UNAVAILABLE, ex.getErrorCode());
    }

    @Test
    void preferredRailIsCarriedThroughWhenEffectiveIsSameRail() {
        PaymentIntentEntity intent = intentWithMetadata("""
                {
                  "rail_selection": {
                    "effective_rail": "SANDBOX",
                    "preferred_rail": "BANK_TRANSFER"
                  }
                }""");
        AdapterRegistry registry = registryWith(AdapterType.SANDBOX, AdapterType.BANK_TRANSFER);

        RailEnforcementOutcome o = enforcement.resolve(intent, config, registry);

        assertEquals(AdapterType.SANDBOX, o.effectiveRail());
        assertEquals(AdapterType.BANK_TRANSFER, o.preferredRail());
        assertEquals(RailEnforcementReason.EXPLICIT_METADATA, o.reason());
        assertFalse(o.legacyIntent());
    }

    @Test
    void invalidPreferredRailValueIsNulledNotThrown() {
        PaymentIntentEntity intent = intentWithMetadata("""
                {
                  "rail_selection": {
                    "effective_rail": "SANDBOX",
                    "preferred_rail": "GARBAGE_RAIL"
                  }
                }""");
        AdapterRegistry registry = registryWith(AdapterType.SANDBOX);

        RailEnforcementOutcome o = enforcement.resolve(intent, config, registry);

        assertEquals(AdapterType.SANDBOX, o.effectiveRail());
        assertNull(o.preferredRail());
        assertEquals(RailEnforcementReason.EXPLICIT_METADATA, o.reason());
    }

    @Test
    void simulationLockUsesSandboxEvenWhenMetadataIsLegacyShape() {
        PaymentIntentEntity intent = intentWithMetadata("""
                { "impilo_simulation": true }""");
        AdapterRegistry registry = registryWith(AdapterType.SANDBOX, AdapterType.MOBILE_MONEY);

        RailEnforcementOutcome o = enforcement.resolve(intent, config, registry);

        assertEquals(AdapterType.SANDBOX, o.effectiveRail());
        assertEquals(RailEnforcementReason.SIMULATION_LOCK, o.reason());
        assertNotNull(o);
    }
}
