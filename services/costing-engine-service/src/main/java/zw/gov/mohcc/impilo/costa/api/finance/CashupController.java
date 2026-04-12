package zw.gov.mohcc.impilo.costa.api.finance;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.domain.entity.DailyCashupEntity;
import zw.gov.mohcc.impilo.costa.service.CashupService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/finance/cashup")
public class CashupController {

    private final CashupService cashupService;

    public CashupController(CashupService cashupService) {
        this.cashupService = cashupService;
    }

    @PostMapping("/open")
    public ResponseEntity<DailyCashupEntity> open(@RequestBody Map<String, Object> body) {
        var ctx = TrustContextHolder.require();
        UUID facilityId = UUID.fromString(body.get("facility_id").toString());
        String cashierId = body.get("cashier_id").toString();
        LocalDate cashupDate = LocalDate.parse(body.get("cashup_date").toString());
        String shiftId = body.get("shift_id") != null ? body.get("shift_id").toString() : null;
        DailyCashupEntity c = cashupService.openDailyCashup(ctx.tenantId(), facilityId, cashierId, cashupDate, shiftId);
        return ResponseEntity.status(HttpStatus.CREATED).body(c);
    }

    @PutMapping("/{cashupId}/collection")
    public ResponseEntity<DailyCashupEntity> recordCollection(
            @PathVariable("cashupId") UUID cashupId,
            @RequestBody Map<String, Object> body) {
        BigDecimal cash = decimal(body.get("cash"));
        BigDecimal card = decimal(body.get("card"));
        BigDecimal mobile = decimal(body.get("mobile"));
        BigDecimal insurance = decimal(body.get("insurance_billed"));
        return ResponseEntity.ok(cashupService.recordCollection(cashupId, cash, card, mobile, insurance));
    }

    @PutMapping("/{cashupId}/close")
    public ResponseEntity<DailyCashupEntity> close(
            @PathVariable("cashupId") UUID cashupId,
            @RequestBody Map<String, Object> body) {
        String supervisorId = body.get("supervisor_id").toString();
        BigDecimal totalBilled = decimal(body.get("total_billed"));
        String notes = body.get("notes") != null ? body.get("notes").toString() : null;
        return ResponseEntity.ok(cashupService.closeCashup(cashupId, supervisorId, totalBilled, notes));
    }

    @GetMapping
    public ResponseEntity<List<DailyCashupEntity>> byFacilityDate(
            @RequestParam("facility_id") UUID facilityId,
            @RequestParam("date") String date) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(cashupService.findByFacilityAndDate(ctx.tenantId(), facilityId, LocalDate.parse(date)));
    }

    private static BigDecimal decimal(Object v) {
        if (v == null) {
            return null;
        }
        return new BigDecimal(v.toString());
    }
}
