package zw.gov.mohcc.impilo.procurement.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.procurement.core.GoodsReceivedService;
import zw.gov.mohcc.impilo.procurement.core.PurchaseOrderService;
import zw.gov.mohcc.impilo.procurement.core.SupplierInvoiceService;
import zw.gov.mohcc.impilo.procurement.persistence.entity.*;
import zw.gov.mohcc.impilo.procurement.persistence.repository.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
public class InternalProcApi {

    private final SupplierRepository supplierRepository;
    private final RequisitionRepository requisitionRepository;
    private final RequisitionLineRepository requisitionLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PoLineRepository poLineRepository;
    private final GoodsReceivedRepository goodsReceivedRepository;
    private final GrnLineRepository grnLineRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final RfqRepository rfqRepository;
    private final RfqResponseRepository rfqResponseRepository;
    private final PurchaseOrderService purchaseOrderService;
    private final GoodsReceivedService goodsReceivedService;
    private final SupplierInvoiceService supplierInvoiceService;

    public InternalProcApi(SupplierRepository supplierRepository,
                           RequisitionRepository requisitionRepository,
                           RequisitionLineRepository requisitionLineRepository,
                           PurchaseOrderRepository purchaseOrderRepository,
                           PoLineRepository poLineRepository,
                           GoodsReceivedRepository goodsReceivedRepository,
                           GrnLineRepository grnLineRepository,
                           SupplierInvoiceRepository supplierInvoiceRepository,
                           RfqRepository rfqRepository,
                           RfqResponseRepository rfqResponseRepository,
                           PurchaseOrderService purchaseOrderService,
                           GoodsReceivedService goodsReceivedService,
                           SupplierInvoiceService supplierInvoiceService) {
        this.supplierRepository = supplierRepository;
        this.requisitionRepository = requisitionRepository;
        this.requisitionLineRepository = requisitionLineRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.poLineRepository = poLineRepository;
        this.goodsReceivedRepository = goodsReceivedRepository;
        this.grnLineRepository = grnLineRepository;
        this.supplierInvoiceRepository = supplierInvoiceRepository;
        this.rfqRepository = rfqRepository;
        this.rfqResponseRepository = rfqResponseRepository;
        this.purchaseOrderService = purchaseOrderService;
        this.goodsReceivedService = goodsReceivedService;
        this.supplierInvoiceService = supplierInvoiceService;
    }

    private UUID tenant() {
        return UUID.fromString(RequestContextHolder.require().tenantId());
    }

    @GetMapping("/internal/v1/procurement/suppliers")
    public List<SupplierEntity> suppliers() {
        return supplierRepository.findByTenantIdOrderByNameAsc(tenant());
    }

    @PostMapping("/internal/v1/procurement/suppliers")
    public SupplierEntity createSupplier(@RequestBody SupplierEntity s) {
        s.setTenantId(tenant());
        return supplierRepository.save(s);
    }

    @GetMapping("/internal/v1/procurement/requisitions")
    public List<RequisitionEntity> requisitions() {
        return requisitionRepository.findByTenantIdOrderByCreatedAtDesc(tenant());
    }

    @PostMapping("/internal/v1/procurement/requisitions")
    public RequisitionEntity createReq(@RequestBody RequisitionEntity r) {
        r.setTenantId(tenant());
        return requisitionRepository.save(r);
    }

    @GetMapping("/internal/v1/procurement/requisitions/{id}/lines")
    public List<RequisitionLineEntity> reqLines(@PathVariable UUID id) {
        return requisitionLineRepository.findByRequisitionId(id);
    }

    @PostMapping("/internal/v1/procurement/requisitions/{id}/lines")
    public RequisitionLineEntity addReqLine(@PathVariable UUID id, @RequestBody RequisitionLineEntity line) {
        line.setRequisitionId(id);
        return requisitionLineRepository.save(line);
    }

    @GetMapping("/internal/v1/procurement/purchase-orders")
    public List<PurchaseOrderEntity> pos() {
        return purchaseOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenant());
    }

    @PostMapping("/internal/v1/procurement/purchase-orders")
    public PurchaseOrderEntity createPo(@RequestBody PurchaseOrderEntity po) throws Exception {
        po.setTenantId(tenant());
        return purchaseOrderService.createAndPublish(po);
    }

    @GetMapping("/internal/v1/procurement/purchase-orders/{id}/lines")
    public List<PoLineEntity> poLines(@PathVariable UUID id) {
        return poLineRepository.findByPoId(id);
    }

    @PostMapping("/internal/v1/procurement/purchase-orders/{id}/lines")
    public PoLineEntity addPoLine(@PathVariable UUID id, @RequestBody PoLineEntity line) {
        line.setPoId(id);
        return poLineRepository.save(line);
    }

    @GetMapping("/internal/v1/procurement/grn")
    public List<GoodsReceivedEntity> grns() {
        return goodsReceivedRepository.findByTenantIdOrderByReceivedAtDesc(tenant());
    }

    @PostMapping("/internal/v1/procurement/grn")
    public GoodsReceivedEntity createGrn(@RequestBody JsonNode body) throws Exception {
        GoodsReceivedEntity grn = new GoodsReceivedEntity();
        grn.setTenantId(tenant());
        grn.setGrnNumber(body.get("grnNumber").asText());
        grn.setPoId(UUID.fromString(body.get("poId").asText()));
        grn.setSupplierId(UUID.fromString(body.get("supplierId").asText()));
        grn.setReceivedBy(body.path("receivedBy").asText(null));
        List<GrnLineEntity> lines = new ArrayList<>();
        for (JsonNode ln : body.withArray("lines")) {
            GrnLineEntity gl = new GrnLineEntity();
            gl.setPoLineId(UUID.fromString(ln.get("poLineId").asText()));
            gl.setQuantityReceived(new BigDecimal(ln.path("quantityReceived").asText("0")));
            gl.setQuantityRejected(ln.has("quantityRejected") ? new BigDecimal(ln.get("quantityRejected").asText()) : BigDecimal.ZERO);
            gl.setBatchNumber(ln.path("batchNumber").asText(null));
            lines.add(gl);
        }
        return goodsReceivedService.receive(grn, lines);
    }

    @GetMapping("/internal/v1/procurement/invoices")
    public List<SupplierInvoiceEntity> invoices() {
        return supplierInvoiceRepository.findByTenantIdOrderByDueDateAsc(tenant());
    }

    @PostMapping("/internal/v1/procurement/invoices")
    public SupplierInvoiceEntity createInvoice(@RequestBody SupplierInvoiceEntity inv) {
        inv.setTenantId(tenant());
        return supplierInvoiceRepository.save(inv);
    }

    @PostMapping("/internal/v1/procurement/invoices/{id}/match")
    public SupplierInvoiceEntity match(@PathVariable UUID id) throws Exception {
        return supplierInvoiceService.attemptMatch(tenant(), id);
    }

    @PostMapping("/internal/v1/procurement/invoices/{id}/pay")
    public SupplierInvoiceEntity payInvoice(@PathVariable UUID id) throws Exception {
        return supplierInvoiceService.markPaid(tenant(), id);
    }

    @GetMapping("/internal/v1/procurement/rfqs")
    public List<RfqEntity> rfqs() {
        return rfqRepository.findByTenantIdOrderByCreatedAtDesc(tenant());
    }

    @PostMapping("/internal/v1/procurement/rfqs")
    public RfqEntity createRfq(@RequestBody RfqEntity r) {
        r.setTenantId(tenant());
        return rfqRepository.save(r);
    }

    @PostMapping("/internal/v1/procurement/rfqs/{id}/responses")
    public RfqResponseEntity addResponse(@PathVariable UUID id, @RequestBody RfqResponseEntity resp) {
        resp.setRfqId(id);
        return rfqResponseRepository.save(resp);
    }

    @GetMapping("/internal/v1/procurement/rfqs/{id}/responses")
    public List<RfqResponseEntity> listResponses(@PathVariable UUID id) {
        return rfqResponseRepository.findByRfqId(id);
    }
}
