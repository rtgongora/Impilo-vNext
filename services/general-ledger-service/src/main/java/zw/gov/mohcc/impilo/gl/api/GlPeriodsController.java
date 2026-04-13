package zw.gov.mohcc.impilo.gl.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.gl.core.FiscalPeriodService;
import zw.gov.mohcc.impilo.gl.persistence.entity.FiscalPeriodEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/gl/periods")
public class GlPeriodsController {

    private final FiscalPeriodService fiscalPeriodService;

    public GlPeriodsController(FiscalPeriodService fiscalPeriodService) {
        this.fiscalPeriodService = fiscalPeriodService;
    }

    @GetMapping("/open")
    public ResponseEntity<List<FiscalPeriodEntity>> open() {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        return ResponseEntity.ok(fiscalPeriodService.openPeriods(tenant));
    }

    @PostMapping("/ensure")
    public ResponseEntity<FiscalPeriodEntity> ensure(@RequestParam("date") String date) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        return ResponseEntity.ok(fiscalPeriodService.ensurePeriodForDate(tenant, LocalDate.parse(date)));
    }

    @PostMapping("/{periodId}/close")
    public ResponseEntity<FiscalPeriodEntity> close(@PathVariable UUID periodId, @RequestBody(required = false) Map<String, String> body) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        String by = body != null && body.get("closedBy") != null ? body.get("closedBy") : "system";
        return ResponseEntity.ok(fiscalPeriodService.closePeriod(tenant, periodId, by));
    }

    @PostMapping("/year-end")
    public ResponseEntity<Void> yearEnd(@RequestParam int fiscalYear, @RequestBody(required = false) Map<String, String> body) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        String actor = body != null && body.get("actor") != null ? body.get("actor") : "system";
        fiscalPeriodService.yearEndClose(tenant, fiscalYear, actor);
        return ResponseEntity.accepted().build();
    }
}
