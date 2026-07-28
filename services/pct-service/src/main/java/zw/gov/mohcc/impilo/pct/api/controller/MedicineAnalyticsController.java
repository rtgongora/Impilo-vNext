package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.pct.core.clinical.MedicineAnalyticsService;

import java.time.LocalDate;
import java.util.Map;

/**
 * The §21 adult-medicine analytics reads that are not programme-enrolment aggregates.
 *
 * <p>Served by PCT because PCT owns the data. reporting-service holds a separate database and cannot
 * see {@code pct.*} — a report definition naming these tables would be registered, ACTIVE and unable
 * to run, which is the state five governed report definitions in this estate are already in.</p>
 *
 * <p>Every path segment here is a literal, so no route can be shadowed by an identifier pattern.
 * Authorization is enforced upstream at Envoy ext_authz; these are tenant-scoped aggregates and
 * return no patient identifiers.</p>
 *
 * <p>The indicators §21 asks for that are <em>not</em> here are listed, with the missing data and its
 * owning service, in {@code docs/clinical/adult-medicine-domain-pack/analytics-coverage.md}.</p>
 */
@RestController
@RequestMapping("/v1/medicine-analytics")
public class MedicineAnalyticsController {

    private final MedicineAnalyticsService service;

    public MedicineAnalyticsController(MedicineAnalyticsService service) {
        this.service = service;
    }

    /** §21 disease detection — what was newly recorded, by code and certainty. */
    @GetMapping("/detection")
    public ResponseEntity<Map<String, Object>> detection(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        return ResponseEntity.ok(Map.of("data", service.detection(category, date(from), date(to))));
    }

    /** §21 diagnostic delays — reported onset to confirmed diagnosis, in days. */
    @GetMapping("/diagnostic-interval")
    public ResponseEntity<Map<String, Object>> diagnosticInterval(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        return ResponseEntity.ok(Map.of("data", service.diagnosticInterval(date(from), date(to))));
    }

    /** §21 complications — recorded against the condition they arose from. */
    @GetMapping("/complications")
    public ResponseEntity<Map<String, Object>> complications(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        return ResponseEntity.ok(Map.of("data", service.complications(date(from), date(to))));
    }

    /** The due half of §21 follow-up: review state of the open problem list. */
    @GetMapping("/review-state")
    public ResponseEntity<Map<String, Object>> reviewState(
            @RequestParam(value = "as_of", required = false) String asOf) {
        return ResponseEntity.ok(Map.of("data", service.reviewState(date(asOf))));
    }

    /** §21 polypharmacy — medicines per completed reconciliation. */
    @GetMapping("/polypharmacy")
    public ResponseEntity<Map<String, Object>> polypharmacy(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "threshold", required = false) Integer threshold) {
        return ResponseEntity.ok(Map.of("data",
                service.polypharmacy(date(from), date(to), threshold)));
    }

    /** §21 palliative access, the episode mix, and episode-level mortality. */
    @GetMapping("/episodes")
    public ResponseEntity<Map<String, Object>> episodes(
            @RequestParam(value = "facility_id", required = false) String facilityId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        return ResponseEntity.ok(Map.of("data", service.episodes(facilityId, date(from), date(to))));
    }

    /** The coverage half of §21 functional outcomes. */
    @GetMapping("/functional-assessments")
    public ResponseEntity<Map<String, Object>> functionalAssessments(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        return ResponseEntity.ok(Map.of("data",
                service.functionalAssessments(date(from), date(to))));
    }

    /** ISO date or null — an unparseable value must 400, never silently become "today". */
    private static LocalDate date(String value) {
        return (value == null || value.isBlank()) ? null : LocalDate.parse(value.trim());
    }
}
