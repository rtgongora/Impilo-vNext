package zw.gov.mohcc.impilo.costa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.enums.BillLineKind;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Costing for outreach and delivery services: transport, per-diem, cold-chain,
 * delivery fees, community health bundles.
 */
@Service
public class OutreachCostingService {

    private final BillService billService;

    public OutreachCostingService(BillService billService) {
        this.billService = billService;
    }

    @Transactional
    public void postTransport(String billId, BigDecimal distanceKm, BigDecimal ratePerKm,
                              String vehicleType) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("distance_km", distanceKm);
        ctx.put("rate_per_km", ratePerKm);
        ctx.put("vehicle_type", vehicleType);
        ctx.put("weighted_avg_cost", ratePerKm);

        billService.postLine(billId, "TRANSPORT-" + vehicleType,
                "Transport — " + distanceKm + " km (" + vehicleType + ")",
                BillLineKind.TRANSPORT, distanceKm,
                CostMethodType.TARIFF, "costa.outreach", null, ctx);
    }

    @Transactional
    public void postPerDiem(String billId, int days, int staffCount, String cadre) {
        BigDecimal qty = BigDecimal.valueOf((long) days * staffCount);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("days", days);
        ctx.put("staff_count", staffCount);
        ctx.put("cadre", cadre);

        billService.postLine(billId, "PERDIEM-" + cadre,
                "Per diem — " + cadre + " (" + days + " days x " + staffCount + " staff)",
                BillLineKind.PER_DIEM, qty,
                CostMethodType.TARIFF, "costa.outreach", null, ctx);
    }

    @Transactional
    public void postColdChain(String billId, String logisticsType, BigDecimal qty) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("logistics_type", logisticsType);

        billService.postLine(billId, "COLDCHAIN-" + logisticsType,
                "Cold chain logistics — " + logisticsType,
                BillLineKind.COLD_CHAIN, qty,
                CostMethodType.TARIFF, "costa.outreach", null, ctx);
    }

    @Transactional
    public void postDeliveryFee(String billId, String deliveryZone, BigDecimal fee) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("delivery_zone", deliveryZone);
        ctx.put("weighted_avg_cost", fee);

        billService.postLine(billId, "DELIVERY-" + deliveryZone,
                "Home delivery — zone " + deliveryZone,
                BillLineKind.FEE, BigDecimal.ONE,
                CostMethodType.TARIFF, "msika.flow.order", null, ctx);
    }

    @Transactional
    public void postCommunityBundle(String billId, String bundleCode, String description) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("bundle_code", bundleCode);

        billService.postLine(billId, bundleCode, description,
                BillLineKind.SERVICE, BigDecimal.ONE,
                CostMethodType.TARIFF, "costa.outreach", bundleCode, ctx);
    }
}
