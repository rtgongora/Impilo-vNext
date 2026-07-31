package zw.gov.mohcc.impilo.clinical.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.clinical.maternal.EmergencyBundleEngine;
import zw.gov.mohcc.impilo.clinical.maternal.EmergencyBundleService;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Postpartum haemorrhage and eclampsia emergency-bundle assessment over HTTP.
 *
 * <p>Stateless decision layer — the episode and recorded steps belong to the caller's encounter
 * record, not here.
 */
@RestController
@RequestMapping("/internal/v1/clinical/maternal/emergency-bundles")
public class EmergencyBundleController {

    private final EmergencyBundleService bundles;

    public EmergencyBundleController(EmergencyBundleService bundles) {
        this.bundles = bundles;
    }

    public record AssessRequest(
            List<String> completedSteps,
            Long minutesSinceTrigger,
            Long minutesSinceLastObservation,
            Boolean controlConfirmed,
            Boolean clinicianConfirmedClose) {
    }

    @PostMapping("/pph/assess")
    public ResponseEntity<Map<String, Object>> assessPph(@RequestBody(required = false) AssessRequest request) {
        return assess(
                EmergencyBundleService.PPH_BUNDLE,
                bundles.assessPph(
                        completedSteps(request),
                        minutes(request == null ? null : request.minutesSinceTrigger()),
                        minutes(request == null ? null : request.minutesSinceLastObservation()),
                        request == null ? null : request.controlConfirmed(),
                        Boolean.TRUE.equals(request == null ? null : request.clinicianConfirmedClose())),
                "pph_bundle_unavailable",
                "The PPH first-response bundle could not be loaded, so no step could be assessed. "
                        + "This is not a statement that the emergency is resolved.");
    }

    @PostMapping("/eclampsia/assess")
    public ResponseEntity<Map<String, Object>> assessEclampsia(
            @RequestBody(required = false) AssessRequest request) {
        return assess(
                EmergencyBundleService.ECLAMPSIA_BUNDLE,
                bundles.assessEclampsia(
                        completedSteps(request),
                        minutes(request == null ? null : request.minutesSinceTrigger()),
                        minutes(request == null ? null : request.minutesSinceLastObservation()),
                        request == null ? null : request.controlConfirmed(),
                        Boolean.TRUE.equals(request == null ? null : request.clinicianConfirmedClose())),
                "eclampsia_bundle_unavailable",
                "The eclampsia first-response bundle could not be loaded, so no step could be assessed. "
                        + "This is not a statement that the emergency is resolved.");
    }

    private ResponseEntity<Map<String, Object>> assess(String resourcePath,
                                                       EmergencyBundleEngine.Assessment assessment,
                                                       String unavailableCode,
                                                       String unavailableMessage) {
        if (bundles.bundle(resourcePath) == null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", unavailableCode);
            body.put("message", unavailableMessage);
            return ResponseEntity.status(502).body(Map.of("data", body));
        }
        return ResponseEntity.ok(Map.of("data", toMap(assessment)));
    }

    private static Set<String> completedSteps(AssessRequest request) {
        if (request == null || request.completedSteps() == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(request.completedSteps());
    }

    private static long minutes(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private static Map<String, Object> toMap(EmergencyBundleEngine.Assessment assessment) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", assessment.status().name());
        data.put("steps", assessment.steps().stream().map(EmergencyBundleController::step).toList());
        data.put("outstanding_mandatory", assessment.outstandingMandatory());
        data.put("may_close", assessment.mayClose());
        data.put("note", assessment.note());
        return data;
    }

    private static Map<String, Object> step(EmergencyBundleEngine.StepVerdict verdict) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", verdict.code());
        m.put("name", verdict.name());
        m.put("status", verdict.status().name());
        m.put("mandatory", verdict.mandatory());
        m.put("action", verdict.action());
        return m;
    }
}
