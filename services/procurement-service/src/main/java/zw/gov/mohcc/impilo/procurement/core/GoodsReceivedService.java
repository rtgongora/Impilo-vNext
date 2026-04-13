package zw.gov.mohcc.impilo.procurement.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.procurement.persistence.entity.*;
import zw.gov.mohcc.impilo.procurement.persistence.repository.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GoodsReceivedService {
    private final GoodsReceivedRepository goodsReceivedRepository;
    private final GrnLineRepository grnLineRepository;
    private final PoLineRepository poLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProcOutboxWriter outboxWriter;

    public GoodsReceivedService(GoodsReceivedRepository goodsReceivedRepository,
                                GrnLineRepository grnLineRepository,
                                PoLineRepository poLineRepository,
                                PurchaseOrderRepository purchaseOrderRepository,
                                ProcOutboxWriter outboxWriter) {
        this.goodsReceivedRepository = goodsReceivedRepository;
        this.grnLineRepository = grnLineRepository;
        this.poLineRepository = poLineRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public GoodsReceivedEntity receive(GoodsReceivedEntity grn, List<GrnLineEntity> lines) throws Exception {
        PurchaseOrderEntity po = purchaseOrderRepository.findById(grn.getPoId()).orElseThrow();
        GoodsReceivedEntity saved = goodsReceivedRepository.save(grn);
        List<Map<String, Object>> lineMaps = new ArrayList<>();
        UUID facilityId = po.getFacilityId();
        UUID storeId = facilityId != null
                ? UUID.nameUUIDFromBytes(("PROC-STORE-" + facilityId).getBytes(StandardCharsets.UTF_8))
                : UUID.nameUUIDFromBytes(("PROC-STORE-DEFAULT").getBytes(StandardCharsets.UTF_8));
        for (GrnLineEntity ln : lines) {
            ln.setGrnId(saved.getGrnId());
            grnLineRepository.save(ln);
            PoLineEntity pl = poLineRepository.findById(ln.getPoLineId()).orElseThrow();
            BigDecimal rcv = ln.getQuantityReceived() != null ? ln.getQuantityReceived() : BigDecimal.ZERO;
            pl.setReceivedQuantity(pl.getReceivedQuantity().add(rcv));
            poLineRepository.save(pl);
            Map<String, Object> row = new HashMap<>();
            row.put("itemCode", pl.getItemCode());
            row.put("quantityReceived", rcv);
            row.put("grnLineId", ln.getLineId().toString());
            row.put("poLineId", pl.getLineId().toString());
            lineMaps.add(row);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", saved.getTenantId().toString());
        if (facilityId != null) {
            payload.put("facilityId", facilityId.toString());
        }
        payload.put("storeId", storeId.toString());
        payload.put("grnId", saved.getGrnId().toString());
        payload.put("poId", po.getPoId().toString());
        payload.put("lines", lineMaps);
        outboxWriter.publish(saved.getTenantId(), "GOODS_RECEIVED", saved.getGrnId().toString(), "grn", "received",
                "proc:grn:" + saved.getGrnId(), payload);
        return saved;
    }
}
