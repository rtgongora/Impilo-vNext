package zw.gov.mohcc.impilo.referral.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.referral.core.ReferralService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/referrals")
public class ReferralController {

    private final ReferralService referralService;

    public ReferralController(ReferralService referralService) {
        this.referralService = referralService;
    }

    @GetMapping
    public List<Map<String, Object>> listReferrals(@RequestParam String patientId) {
        return referralService.listReferrals(patientId);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createReferral(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referralService.createReferral(body));
    }

    @GetMapping("/{id}")
    public Map<String, Object> getReferral(@PathVariable String id) {
        return referralService.getReferral(id);
    }

    @PostMapping("/{id}/accept")
    public Map<String, Object> acceptReferral(@PathVariable String id) {
        return referralService.acceptReferral(id);
    }

    @PostMapping("/{id}/respond")
    public Map<String, Object> respondReferral(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return referralService.respondReferral(id, body);
    }

    @PostMapping("/{id}/complete")
    public Map<String, Object> completeReferral(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        return referralService.completeReferral(id, body);
    }
}
