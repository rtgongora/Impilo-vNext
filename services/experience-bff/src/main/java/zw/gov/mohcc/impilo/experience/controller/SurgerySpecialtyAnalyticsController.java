package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.SurgeryServiceClient;

/**
 * Experience-BFF facade for the specialty catalogue (SB-4) and surgical analytics (§23).
 *
 * <p>Sibling of {@link SurgeryController}: that class owns the episode-scoped family under
 * {@code /internal/v1/surgery/episodes/**}; this one owns the catalogue under
 * {@code /internal/v1/surgery/specialties/**} and analytics under
 * {@code /internal/v1/surgery/analytics/**}. tshepo-authz V305 gates each route; the catalogue
 * pin is {@code /surgery/specialties} (not {@code /surgery/episodes}) and the analytics pin is
 * {@code /surgery/analytics} (not {@code /procedures/analytics}) — proven in
 * SurgeryReachabilityRouteShapeTest.</p>
 *
 * <p>Pure pass-through: no try/catch, no invented shapes. Downstream failures propagate to
 * {@code BffGlobalExceptionHandler}.</p>
 */
@RestController
@RequestMapping("/internal/v1/surgery")
public class SurgerySpecialtyAnalyticsController {

    private final SurgeryServiceClient surgery;

    public SurgerySpecialtyAnalyticsController(SurgeryServiceClient surgery) {
        this.surgery = surgery;
    }

    @GetMapping("/specialties/indications")
    public ResponseEntity<String> specialtyIndications(@RequestParam String specialty) {
        return surgery.specialtyIndications(specialty);
    }

    @GetMapping("/specialties/templates")
    public ResponseEntity<String> specialtyTemplates(@RequestParam String specialty) {
        return surgery.specialtyTemplates(specialty);
    }

    @GetMapping("/analytics/indicators")
    public ResponseEntity<String> analyticsIndicators() {
        return surgery.analyticsIndicators();
    }

    @GetMapping("/analytics/indicator")
    public ResponseEntity<String> analyticsIndicator(@RequestParam String code) {
        return surgery.analyticsIndicator(code);
    }
}
