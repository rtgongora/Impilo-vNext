package zw.gov.mohcc.impilo.procurement.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.procurement.core.GoodsReceivedService;
import zw.gov.mohcc.impilo.procurement.core.PurchaseOrderService;
import zw.gov.mohcc.impilo.procurement.core.SupplierInvoiceService;
import zw.gov.mohcc.impilo.procurement.persistence.entity.SupplierEntity;
import zw.gov.mohcc.impilo.procurement.persistence.repository.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalProcApiContractSurfaceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000012");

    @Mock private SupplierRepository supplierRepository;
    @Mock private RequisitionRepository requisitionRepository;
    @Mock private RequisitionLineRepository requisitionLineRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private PoLineRepository poLineRepository;
    @Mock private GoodsReceivedRepository goodsReceivedRepository;
    @Mock private GrnLineRepository grnLineRepository;
    @Mock private SupplierInvoiceRepository supplierInvoiceRepository;
    @Mock private RfqRepository rfqRepository;
    @Mock private RfqResponseRepository rfqResponseRepository;
    @Mock private PurchaseOrderService purchaseOrderService;
    @Mock private GoodsReceivedService goodsReceivedService;
    @Mock private SupplierInvoiceService supplierInvoiceService;

    private InternalProcApi api;

    @BeforeEach
    void setUp() {
        api = new InternalProcApi(
                supplierRepository,
                requisitionRepository,
                requisitionLineRepository,
                purchaseOrderRepository,
                poLineRepository,
                goodsReceivedRepository,
                grnLineRepository,
                supplierInvoiceRepository,
                rfqRepository,
                rfqResponseRepository,
                purchaseOrderService,
                goodsReceivedService,
                supplierInvoiceService);
        RequestContextHolder.set(RequestContext.of(
                TENANT.toString(), "national", "req-proc", "corr-proc", null, null, null));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void listSuppliersScopesByTenant() {
        SupplierEntity supplier = new SupplierEntity();
        when(supplierRepository.findByTenantIdOrderByNameAsc(TENANT)).thenReturn(List.of(supplier));

        List<SupplierEntity> result = api.suppliers();

        assertThat(result).containsExactly(supplier);
        verify(supplierRepository).findByTenantIdOrderByNameAsc(TENANT);
    }
}
