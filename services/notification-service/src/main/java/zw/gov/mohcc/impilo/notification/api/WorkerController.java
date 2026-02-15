package zw.gov.mohcc.impilo.notification.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.notification.api.dto.WorkerResponse;
import zw.gov.mohcc.impilo.notification.service.WorkerService;

@RestController
@RequestMapping("/internal/v1/worker")
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @PostMapping("/run")
    public ResponseEntity<WorkerResponse> runWorker(@RequestParam(defaultValue = "10") int limit) {
        RequestContextHolder.require();

        WorkerResponse response = workerService.processPending(limit);
        return ResponseEntity.ok(response);
    }
}
