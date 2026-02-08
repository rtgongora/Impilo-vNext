package zw.gov.mohcc.impilo.costa.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.enums.BillLineKind;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extended costing for inpatient encounters.
 * Computes bed-days, food, laundry, oxygen, theatre, implants, and nursing time.
 */
@Service
public class InpatientCostingService {

    private static final Logger log = LoggerFactory.getLogger(InpatientCostingService.class);

    private final BillService billService;

    public InpatientCostingService(BillService billService) {
        this.billService = billService;
    }

    @Transactional
    public void postBedDays(String billId, String wardCode, String levelOfCare,
                            OffsetDateTime admissionDate, OffsetDateTime dischargeDate) {
        long days = calculateBedDays(admissionDate, dischargeDate);
        if (days <= 0) return;

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("ward_code", wardCode);
        ctx.put("level_of_care", levelOfCare);
        ctx.put("admission_date", admissionDate.toString());
        ctx.put("discharge_date", dischargeDate.toString());

        billService.postLine(billId, "BEDDAY-" + levelOfCare,
                "Bed day — " + levelOfCare + " (" + wardCode + ")",
                BillLineKind.BEDDAY, BigDecimal.valueOf(days),
                CostMethodType.TARIFF, "pct.admission", null, ctx);
    }

    @Transactional
    public void postFood(String billId, String dietCategory, long days) {
        if (days <= 0) return;

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("diet_category", dietCategory);

        billService.postLine(billId, "FOOD-" + dietCategory,
                "Catering — " + dietCategory + " diet",
                BillLineKind.FOOD, BigDecimal.valueOf(days),
                CostMethodType.TARIFF, "pct.admission", null, ctx);
    }

    @Transactional
    public void postLaundry(String billId, long days) {
        if (days <= 0) return;

        billService.postLine(billId, "LAUNDRY-INPATIENT",
                "Linen/laundry per day",
                BillLineKind.LAUNDRY, BigDecimal.valueOf(days),
                CostMethodType.TARIFF, "pct.admission", null, Map.of());
    }

    @Transactional
    public void postOxygen(String billId, BigDecimal flowRateLpm, BigDecimal hours) {
        if (hours.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal litres = flowRateLpm.multiply(hours).multiply(new BigDecimal("60"));
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("flow_rate_lpm", flowRateLpm);
        ctx.put("hours", hours);
        ctx.put("total_litres", litres);

        billService.postLine(billId, "OXYGEN-MEDICAL",
                "Medical oxygen (" + flowRateLpm + " L/min x " + hours + " hrs)",
                BillLineKind.OXYGEN, litres,
                CostMethodType.TARIFF, "pct.admission", null, ctx);
    }

    @Transactional
    public void postTheatreTime(String billId, int minutes, String procedureCode, boolean hasAnaesthesia) {
        if (minutes <= 0) return;

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("procedure_code", procedureCode);
        ctx.put("theatre_minutes", minutes);
        ctx.put("has_anaesthesia", hasAnaesthesia);

        billService.postLine(billId, "THEATRE-" + procedureCode,
                "Theatre time — " + minutes + " min",
                BillLineKind.THEATRE, BigDecimal.valueOf(minutes),
                CostMethodType.TARIFF, "oros.order", procedureCode, ctx);

        if (hasAnaesthesia) {
            billService.postLine(billId, "ANAESTHESIA-" + procedureCode,
                    "Anaesthesia — " + minutes + " min",
                    BillLineKind.THEATRE, BigDecimal.valueOf(minutes),
                    CostMethodType.TARIFF, "oros.order", procedureCode,
                    Map.of("component", "ANAESTHESIA"));
        }
    }

    @Transactional
    public void postImplant(String billId, String implantCode, String description,
                            BigDecimal qty, BigDecimal unitCost) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("weighted_avg_cost", unitCost);
        ctx.put("implant_code", implantCode);

        billService.postLine(billId, implantCode, description,
                BillLineKind.IMPLANT, qty,
                CostMethodType.STOCK_AVG, "inventory.issue", implantCode, ctx);
    }

    @Transactional
    public void postNursingTime(String billId, BigDecimal hours, String cadre) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("nursing_cadre", cadre);

        billService.postLine(billId, "NURSING-" + cadre,
                "Nursing time — " + cadre + " (" + hours + " hrs)",
                BillLineKind.NURSING, hours,
                CostMethodType.MICRO, "pct.encounter", null, ctx);
    }

    @Transactional
    public void postOverhead(String billId, String poolName, BigDecimal driverQty) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("driver_qty", driverQty);
        ctx.put("pool_name", poolName);

        billService.postLine(billId, "OVERHEAD-" + poolName,
                "Overhead allocation — " + poolName,
                BillLineKind.OVERHEAD, driverQty,
                CostMethodType.ABC, "costa.internal", null, ctx);
    }

    @Transactional
    public void postDischargeCloseout(String billId, OffsetDateTime admissionDate,
                                      OffsetDateTime dischargeDate, String wardCode,
                                      String levelOfCare, String dietCategory) {
        long days = calculateBedDays(admissionDate, dischargeDate);
        postBedDays(billId, wardCode, levelOfCare, admissionDate, dischargeDate);
        postFood(billId, dietCategory != null ? dietCategory : "STANDARD", days);
        postLaundry(billId, days);
    }

    private long calculateBedDays(OffsetDateTime admission, OffsetDateTime discharge) {
        if (admission == null || discharge == null) return 0;
        long days = Duration.between(admission, discharge).toDays();
        return Math.max(days, 1); // Minimum 1 bed-day
    }
}
