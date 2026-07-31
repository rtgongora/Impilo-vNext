package zw.gov.mohcc.impilo.clinical.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.clinical.prescribing.RenalDosingService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renal dosing awareness (Wave 5.5) — advisory bands only, not interaction or formulary checking.
 */
@RestController
@RequestMapping("/internal/v1/medicine/renal-dosing")
public class RenalDosingController {

    private final RenalDosingService service;

    public RenalDosingController(RenalDosingService service) {
        this.service = service;
    }

    @GetMapping("/advise")
    public ResponseEntity<Map<String, Object>> adviseGet(
            @RequestParam(name = "egfr", required = false) Double egfr,
            @RequestParam(name = "drugClass", required = false) String drugClass) {
        return ResponseEntity.ok(Map.of("data", service.advise(egfr, drugClass)));
    }

    @PostMapping("/advise")
    public ResponseEntity<Map<String, Object>> advisePost(@RequestBody(required = false) Map<String, Object> body) {
        Double egfr = null;
        String drugClass = null;
        if (body != null) {
            Object egfrRaw = body.get("egfr");
            if (egfrRaw instanceof Number number) {
                egfr = number.doubleValue();
            }
            Object classRaw = body.get("drugClass");
            if (classRaw != null) {
                drugClass = classRaw.toString();
            }
        }
        return ResponseEntity.ok(Map.of("data", service.advise(egfr, drugClass)));
    }
}
