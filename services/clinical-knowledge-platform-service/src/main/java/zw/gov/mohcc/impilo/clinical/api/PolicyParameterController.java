package zw.gov.mohcc.impilo.clinical.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.clinical.policy.NationalPolicyParameterService;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads a governed national policy parameter as it stood on a date.
 *
 * <p>The response always carries {@code approvalStatus} and {@code verificationStatus} beside the
 * value. A consumer that reads only the value would treat an unverified engineering seed as national
 * policy, which is exactly the confusion these rows exist to prevent.
 */
@RestController
@RequestMapping("/internal/v1/clinical/policy-parameters")
public class PolicyParameterController {

    private final NationalPolicyParameterService service;

    public PolicyParameterController(NationalPolicyParameterService service) {
        this.service = service;
    }

    @GetMapping("/{code}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String code,
            @RequestParam(name = "asOf", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {

        return service.inForce(code, asOf)
                .<ResponseEntity<Map<String, Object>>>map(p -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("parameterCode", p.getParameterCode());
                    data.put("domain", p.getDomain());
                    data.put("valueType", p.getValueType());
                    data.put("value", p.getValueText());
                    data.put("unit", p.getUnit());
                    data.put("effectiveStart", String.valueOf(p.getEffectiveStart()));
                    data.put("effectiveEnd", p.getEffectiveEnd() == null ? null : String.valueOf(p.getEffectiveEnd()));
                    data.put("approvalStatus", p.getApprovalStatus());
                    data.put("verificationStatus", p.getVerificationStatus());
                    data.put("ratified", p.isRatified());
                    data.put("legalBasis", p.getLegalBasis());
                    data.put("contentVersion", p.getContentVersion());
                    return ResponseEntity.ok(Map.of("data", data));
                })
                // No version in force is a real answer, not an error: a consumer must be able to
                // distinguish "no policy covers this date" from "the service is down".
                .orElseGet(() -> ResponseEntity.ok(Map.of("data", Map.of(
                        "parameterCode", code,
                        "inForce", false))));
    }
}
