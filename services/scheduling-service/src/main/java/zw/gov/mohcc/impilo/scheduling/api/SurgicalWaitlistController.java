package zw.gov.mohcc.impilo.scheduling.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.scheduling.core.SurgicalWaitlistService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/internal/scheduling/surgical-waitlist")
public class SurgicalWaitlistController {

    private final SurgicalWaitlistService service;

    public SurgicalWaitlistController(SurgicalWaitlistService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String sort) {
        return service.list(status, sort);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return service.get(id);
    }

    /**
     * Synchronous placement mirror of the referral.surgical.accepted event.
     * Idempotent by referral_id — the same {@code placeOnWaitlist} the Kafka
     * consumer calls. Provided for environments without a wired Kafka bridge and
     * for governed replay; it is NOT a competing case entry point (the case SoR
     * remains inpatient.procedure_episode via booking).
     */
    @PostMapping("/from-referral")
    public ResponseEntity<Map<String, Object>> placeFromReferral(@RequestBody Map<String, Object> body) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        UUID referralId = UUID.fromString(String.valueOf(body.get("referralId")));
        String patientId = String.valueOf(body.get("patientId"));
        String zibo = body.get("zibo") != null ? String.valueOf(body.get("zibo")) : null;
        String priority = body.get("clinicalPriority") != null
                ? String.valueOf(body.get("clinicalPriority")) : "P4";
        Integer timeframe = body.get("targetTimeframeDays") != null
                ? Integer.valueOf(String.valueOf(body.get("targetTimeframeDays"))) : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> special = body.get("specialRequirements") instanceof Map
                ? (Map<String, Object>) body.get("specialRequirements") : null;
        Map<String, Object> placed = service.placeOnWaitlist(
                tenantId, referralId, patientId, zibo, priority, timeframe, special);
        return ResponseEntity.status(HttpStatus.CREATED).body(placed);
    }

    @PostMapping("/{id}/defer")
    public Map<String, Object> defer(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        return service.defer(id, body);
    }

    @PostMapping("/{id}/return")
    public Map<String, Object> returnToWaiting(@PathVariable String id,
                                              @RequestBody(required = false) Map<String, Object> body) {
        return service.returnToWaiting(id, body);
    }
}
