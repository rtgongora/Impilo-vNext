package zw.gov.mohcc.impilo.patientsafety.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.patientsafety.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.patientsafety.api.dto.CaseSummary;
import zw.gov.mohcc.impilo.patientsafety.core.SafetyCaseService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** MCAZ regulator workbench: incoming + priority queues and headline counts. */
@RestController
@RequestMapping("/internal/v1/patient-safety/mcaz")
public class McazWorkbenchController {

    private final SafetyCaseService cases;

    public McazWorkbenchController(SafetyCaseService cases) {
        this.cases = cases;
    }

    @GetMapping("/workbench")
    public ResponseEntity<ApiResponse<Map<String, Object>>> workbench(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        UUID tid = UUID.fromString(tenantId);
        Map<String, Object> body = Map.of(
                "stats", cases.workbenchStats(tid),
                "incoming", cases.incomingQueue(tid),
                "priority", cases.priorityQueue(tid));
        return ResponseEntity.ok(ApiResponse.of(body));
    }

    @GetMapping("/incoming-queue")
    public ResponseEntity<ApiResponse<List<CaseSummary>>> incoming(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(ApiResponse.of(cases.incomingQueue(UUID.fromString(tenantId))));
    }

    @GetMapping("/priority-queue")
    public ResponseEntity<ApiResponse<List<CaseSummary>>> priority(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(ApiResponse.of(cases.priorityQueue(UUID.fromString(tenantId))));
    }
}
