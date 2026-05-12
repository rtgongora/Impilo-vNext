package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.vito.core.PhidPoolService;
import zw.gov.mohcc.impilo.vito.persistence.entity.PhidPoolEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/phid-pool")
public class PhidPoolController {

    private final PhidPoolService phidPoolService;

    public PhidPoolController(PhidPoolService phidPoolService) {
        this.phidPoolService = phidPoolService;
    }

    public record AllocateRequest(String facilityId, int count) {}

    public record ClaimRequest(UUID healthId, String mrsPatientId) {}

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<Map<String, Object>>> allocate(@RequestBody AllocateRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (!"SYSTEM".equals(ctx.actorType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SYSTEM actor required");
        }

        List<PhidPoolService.PhidPoolEntry> entries = phidPoolService.allocate(request.facilityId(), request.count());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("facilityId", request.facilityId());
        body.put("allocated", entries.size());
        body.put("entries", entries);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(body, ctx.correlationId().toString()));
    }

    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<Map<String, Object>>> claim(@RequestBody ClaimRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (!"SYSTEM".equals(ctx.actorType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SYSTEM actor required");
        }

        try {
            PhidPoolEntity entity = phidPoolService.claim(request.healthId(), request.mrsPatientId());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("healthId", entity.getHealthId());
            body.put("phid", entity.getPhid());
            body.put("status", entity.getStatus());

            return ResponseEntity.ok(ApiResponse.ok(body, ctx.correlationId().toString()));
        } catch (AccessDeniedException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(@RequestParam String facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        if (!"SYSTEM".equals(ctx.actorType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SYSTEM actor required");
        }

        PhidPoolService.PhidPoolStats s = phidPoolService.getStats(facilityId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("facilityId", facilityId);
        body.put("available", s.available());
        body.put("assigned", s.assigned());
        body.put("total", s.total());

        return ResponseEntity.ok(ApiResponse.ok(body, ctx.correlationId().toString()));
    }
}
