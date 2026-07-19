package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.tuso.core.FacilityRegistrationService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegistrationCaseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FJ2 — self-service facility registration (D-L5). The registrant lane returns
 * ONE generic receipt shape for every outcome; the steward lane (case queue,
 * incl. steward-only match context) rides the Trust Console policy family.
 * The claimant person is bound by the trusted BFF caller to the session actor.
 */
@RestController
@RequestMapping("/v1/internal/facility-registrations")
public class FacilityRegistrationController {

    private final FacilityRegistrationService registrationService;

    public FacilityRegistrationController(FacilityRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping
    public ResponseEntity<FacilityRegistrationService.RegistrationReceipt> register(
            @RequestBody FacilityRegistrationService.RegisterFacilityRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(registrationService.register(request));
    }

    /** Steward review queue by outcome (Trust Console lane). */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listByOutcome(@RequestParam("outcome") String outcome) {
        List<Map<String, Object>> cases = registrationService.listByOutcome(outcome).stream()
                .map(FacilityRegistrationController::toStewardView)
                .toList();
        return ResponseEntity.ok(Map.of("cases", cases));
    }

    private static Map<String, Object> toStewardView(FacilityRegistrationCaseEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("caseRef", c.getCaseRef());
        m.put("submittedName", c.getSubmittedName());
        m.put("applicantHealthId", c.getApplicantHealthId());
        m.put("outcome", c.getOutcome());
        m.put("matchedFacilityUuid", c.getMatchedFacilityUuid());
        m.put("matchContext", c.getMatchContext());
        m.put("provisionalFacilityUuid", c.getProvisionalFacilityUuid());
        m.put("evidenceRef", c.getEvidenceRef());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }
}
