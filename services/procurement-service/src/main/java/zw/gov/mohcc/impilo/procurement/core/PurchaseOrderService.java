package zw.gov.mohcc.impilo.procurement.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.procurement.persistence.entity.PurchaseOrderEntity;
import zw.gov.mohcc.impilo.procurement.persistence.repository.PurchaseOrderRepository;

import java.util.Map;
import java.util.UUID;

@Service
public class PurchaseOrderService {
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProcOutboxWriter outboxWriter;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository, ProcOutboxWriter outboxWriter) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public PurchaseOrderEntity createAndPublish(PurchaseOrderEntity po) throws Exception {
        PurchaseOrderEntity saved = purchaseOrderRepository.save(po);
        outboxWriter.publish(saved.getTenantId(), "PURCHASE_ORDER", saved.getPoId().toString(), "purchase_order", "created",
                "proc:po:" + saved.getPoId(),
                Map.of("tenantId", saved.getTenantId().toString(), "poId", saved.getPoId().toString(),
                        "poNumber", saved.getPoNumber(), "supplierId", saved.getSupplierId().toString(),
                        "totalAmount", saved.getTotalAmount(), "facilityId",
                        saved.getFacilityId() != null ? saved.getFacilityId().toString() : null));
        return saved;
    }
}
