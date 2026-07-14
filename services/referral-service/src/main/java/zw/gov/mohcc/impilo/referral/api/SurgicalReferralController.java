package zw.gov.mohcc.impilo.referral.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.referral.core.SurgicalReferralService;

import java.util.List;
import java.util.Map;

/**
 * Surgical referral API. The worklist endpoint is a filtered read (receiving-team
 * view) over the same surgical_referral rows — not a separate store.
 */
@RestController
@RequestMapping("/v1/internal/referrals/surgical")
public class SurgicalReferralController {

    private final SurgicalReferralService service;

    public SurgicalReferralController(SurgicalReferralService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSurgicalReferral(body));
    }

    @GetMapping("/worklist")
    public List<Map<String, Object>> worklist(@RequestParam(required = false) String specialty,
                                              @RequestParam(required = false) String decision) {
        return service.worklist(specialty, decision);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return service.getSurgicalReferral(id);
    }

    @PostMapping("/{id}/decide")
    public Map<String, Object> decide(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return service.decideSurgicalReferral(id, body);
    }
}
