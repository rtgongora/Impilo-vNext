package zw.gov.mohcc.impilo.mushex.rail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterReadinessStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterRegistry;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterResponse;
import zw.gov.mohcc.impilo.mushex.service.adapter.PaymentRailAdapter;
import zw.gov.mohcc.impilo.mushex.service.rail.AdapterReadiness;
import zw.gov.mohcc.impilo.mushex.service.rail.AdapterReadinessService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AdapterReadinessService}. Runs without Spring; constructs a
 * real {@link AdapterRegistry} per case.
 *
 * <p>Covers every readiness branch in the design (NOT_REGISTERED, DISABLED,
 * READY_SANDBOX, CREDENTIALS_MISSING, READY_LIVE) and the production-default
 * posture (all real-money rails default to DISABLED; SANDBOX defaults to READY_SANDBOX).
 */
class AdapterReadinessServiceTest {

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

    private static AdapterReadiness rowFor(List<AdapterReadiness> rows, AdapterType type) {
        return rows.stream().filter(r -> r.adapterType() == type).findFirst().orElseThrow();
    }

    // ----- cases --------------------------------------------------------------------

    @Test
    void describeAll_returnsOneRowPerAdapterType_inStableOrder() {
        AdapterReadinessService svc = new AdapterReadinessService(registryWith(AdapterType.SANDBOX), properties);

        List<AdapterReadiness> rows = svc.describeAll();

        assertEquals(AdapterType.values().length, rows.size(),
                "describeAll must return one row per AdapterType, including unregistered ones");
        for (int i = 0; i < AdapterType.values().length; i++) {
            assertEquals(AdapterType.values()[i], rows.get(i).adapterType(),
                    "Rows must follow enum declaration order");
        }
    }

    @Test
    void notRegisteredAdapter_isReportedAsNotRegistered_notOmitted() {
        AdapterReadinessService svc = new AdapterReadinessService(registryWith(AdapterType.SANDBOX), properties);

        AdapterReadiness row = svc.describe(AdapterType.MOBILE_MONEY);

        assertEquals(AdapterReadinessStatus.NOT_REGISTERED, row.status());
        assertFalse(row.liveCapable());
        assertFalse(row.sandboxCapable());
        assertNotNull(row.detail());
    }

    @Test
    void sandboxRail_whenEnabled_isReadySandboxOnly() {
        AdapterReadinessService svc = new AdapterReadinessService(registryWith(AdapterType.SANDBOX), properties);

        AdapterReadiness row = svc.describe(AdapterType.SANDBOX);

        assertEquals(AdapterReadinessStatus.READY_SANDBOX, row.status());
        assertFalse(row.liveCapable(), "SANDBOX must never report liveCapable=true");
        assertTrue(row.sandboxCapable());
    }

    @Test
    void sandboxRail_whenDisabled_isDisabled() {
        properties.getAdapters().getSandbox().setEnabled(false);
        AdapterReadinessService svc = new AdapterReadinessService(registryWith(AdapterType.SANDBOX), properties);

        AdapterReadiness row = svc.describe(AdapterType.SANDBOX);

        assertEquals(AdapterReadinessStatus.DISABLED, row.status());
        assertFalse(row.liveCapable());
        assertFalse(row.sandboxCapable());
    }

    @Test
    void realMoneyRail_defaultsToDisabled_inProductionDefaults() {
        AdapterReadinessService svc = new AdapterReadinessService(
                registryWith(AdapterType.MOBILE_MONEY, AdapterType.BANK_TRANSFER, AdapterType.CARD_GATEWAY),
                properties);

        for (AdapterType t : List.of(AdapterType.MOBILE_MONEY, AdapterType.BANK_TRANSFER, AdapterType.CARD_GATEWAY)) {
            AdapterReadiness row = svc.describe(t);
            assertEquals(AdapterReadinessStatus.DISABLED, row.status(),
                    "Real-money rail " + t + " must default to DISABLED in production");
            assertFalse(row.liveCapable());
        }
    }

    @Test
    void realMoneyRail_enabledButCredentialsMissing_isCredentialsMissing_notLiveCapable() {
        properties.getAdapters().getMobileMoney().setEnabled(true);
        AdapterReadinessService svc = new AdapterReadinessService(
                registryWith(AdapterType.MOBILE_MONEY), properties);

        AdapterReadiness row = svc.describe(AdapterType.MOBILE_MONEY);

        assertEquals(AdapterReadinessStatus.CREDENTIALS_MISSING, row.status());
        assertFalse(row.liveCapable(), "Missing credentials must never permit live routing");
    }

    @Test
    void realMoneyRail_enabledAndCredentialsConfigured_isReadyLive() {
        properties.getAdapters().getBankTransfer().setEnabled(true);
        properties.getAdapters().getBankTransfer().setCredentialsConfigured(true);
        AdapterReadinessService svc = new AdapterReadinessService(
                registryWith(AdapterType.BANK_TRANSFER), properties);

        AdapterReadiness row = svc.describe(AdapterType.BANK_TRANSFER);

        assertEquals(AdapterReadinessStatus.READY_LIVE, row.status());
        assertTrue(row.liveCapable());
        assertFalse(row.sandboxCapable());
        assertTrue(row.detail().contains("currently stubs"),
                "Detail string must remind operators that adapters remain stubs today");
    }

    @Test
    void describeAll_inDefaultProductionConfig_reportsSandboxReadyAndRealRailsDisabledOrNotRegistered() {
        AdapterReadinessService svc = new AdapterReadinessService(
                registryWith(AdapterType.SANDBOX, AdapterType.MOBILE_MONEY,
                        AdapterType.BANK_TRANSFER, AdapterType.CARD_GATEWAY),
                properties);

        List<AdapterReadiness> rows = svc.describeAll();

        assertEquals(AdapterReadinessStatus.READY_SANDBOX, rowFor(rows, AdapterType.SANDBOX).status());
        assertEquals(AdapterReadinessStatus.DISABLED, rowFor(rows, AdapterType.MOBILE_MONEY).status());
        assertEquals(AdapterReadinessStatus.DISABLED, rowFor(rows, AdapterType.BANK_TRANSFER).status());
        assertEquals(AdapterReadinessStatus.DISABLED, rowFor(rows, AdapterType.CARD_GATEWAY).status());
        long liveCapable = rows.stream().filter(AdapterReadiness::liveCapable).count();
        assertEquals(0, liveCapable, "Default production posture must have zero live-capable rails");
    }

    @Test
    void describe_neverReadsCredentials_byContract() {
        // The service must compile and execute without ever referencing a credential
        // field. We assert by structure: the only inputs are AdapterRegistry and
        // MushexProperties, and the only MushexProperties fields read are boolean
        // 'enabled' and 'credentialsConfigured' flags. This test exercises every code
        // path and asserts no exception is thrown for any combination of those booleans.
        for (boolean enabled : new boolean[] {false, true}) {
            for (boolean credsConfigured : new boolean[] {false, true}) {
                properties.getAdapters().getMobileMoney().setEnabled(enabled);
                properties.getAdapters().getMobileMoney().setCredentialsConfigured(credsConfigured);
                AdapterReadinessService svc = new AdapterReadinessService(
                        registryWith(AdapterType.MOBILE_MONEY), properties);
                AdapterReadiness row = svc.describe(AdapterType.MOBILE_MONEY);
                assertNotNull(row);
                assertNotNull(row.detail());
            }
        }
    }
}
