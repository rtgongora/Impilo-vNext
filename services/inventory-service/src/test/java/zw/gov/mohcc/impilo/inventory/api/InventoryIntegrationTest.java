package zw.gov.mohcc.impilo.inventory.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.inventory.config.InventoryTestSecurityBeans;
import zw.gov.mohcc.impilo.inventory.core.*;
import zw.gov.mohcc.impilo.inventory.integration.ElmisAdapterIntegration;
import zw.gov.mohcc.impilo.inventory.integration.PharmacyIntegration;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the Inventory service.
 *
 * <p>Validates that the Spring application context loads successfully
 * and that the actuator health endpoint is accessible. Uses MockBean
 * for all core services and integration services to avoid requiring
 * external dependencies (Kafka, PostgreSQL, etc.).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LedgerService ledgerService;

    @MockBean
    private OnHandService onHandService;

    @MockBean
    private FefoService fefoService;

    @MockBean
    private BarcodeLookupService barcodeLookupService;

    @MockBean
    private StockCountService stockCountService;

    @MockBean
    private RequisitionService requisitionService;

    @MockBean
    private HandoverService handoverService;

    @MockBean
    private ReconciliationService reconciliationService;

    @MockBean
    private CapabilityRoutingService capabilityRoutingService;

    @MockBean
    private ItemService itemService;

    @MockBean
    private StoreService storeService;

    @MockBean
    private ConsumptionPostingService consumptionPostingService;

    @MockBean
    private ElmisAdapterIntegration elmisAdapterIntegration;

    @MockBean
    private PharmacyIntegration pharmacyIntegration;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContext ctx = new TrustContext(
                TENANT_ID, "test-user", "PROVIDER", "INVENTORY_MANAGEMENT",
                null, CORRELATION_ID, FACILITY_ID, null, null, AccessMode.INTERNAL);
        TrustContextHolder.set(ctx);
    }

    @Test
    @DisplayName("Application context loads successfully")
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.containsBean("inventoryApplication")).isTrue();
    }

    @Test
    @DisplayName("Actuator health endpoint returns UP")
    void actuatorHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("All controller beans are registered")
    void controllerBeansRegistered() {
        assertThat(applicationContext.containsBean("itemController")).isTrue();
        assertThat(applicationContext.containsBean("onHandController")).isTrue();
        assertThat(applicationContext.containsBean("ledgerController")).isTrue();
        assertThat(applicationContext.containsBean("fefoController")).isTrue();
        assertThat(applicationContext.containsBean("countController")).isTrue();
        assertThat(applicationContext.containsBean("requisitionController")).isTrue();
        assertThat(applicationContext.containsBean("handoverController")).isTrue();
        assertThat(applicationContext.containsBean("reconcileController")).isTrue();
        assertThat(applicationContext.containsBean("internalController")).isTrue();
    }

    @Test
    @DisplayName("All core service mock beans are available")
    void coreServiceBeansAvailable() {
        assertThat(ledgerService).isNotNull();
        assertThat(onHandService).isNotNull();
        assertThat(fefoService).isNotNull();
        assertThat(barcodeLookupService).isNotNull();
        assertThat(stockCountService).isNotNull();
        assertThat(requisitionService).isNotNull();
        assertThat(handoverService).isNotNull();
        assertThat(reconciliationService).isNotNull();
        assertThat(capabilityRoutingService).isNotNull();
        assertThat(itemService).isNotNull();
        assertThat(storeService).isNotNull();
        assertThat(consumptionPostingService).isNotNull();
    }

    @Test
    @DisplayName("Integration beans are available")
    void integrationBeansAvailable() {
        assertThat(elmisAdapterIntegration).isNotNull();
        assertThat(pharmacyIntegration).isNotNull();
    }
}
