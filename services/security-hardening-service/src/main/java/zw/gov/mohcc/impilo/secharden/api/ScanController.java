package zw.gov.mohcc.impilo.secharden.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.secharden.core.ScanService;
import zw.gov.mohcc.impilo.secharden.persistence.entity.ScanResultEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

@RestController
@RequestMapping("/internal/v1/scans")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScanResultEntity>> runScan(
            @RequestBody RunScanRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        ScanResultEntity result = scanService.runScan(
                ctx.tenantId(), ctx.actorId(), ctx.correlationId(),
                request.packId(), request.target(),
                request.passed(), request.failed(), request.skipped(),
                request.details());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(result, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ScanResultEntity>>> listScans() {
        TrustContext ctx = TrustContextHolder.require();

        List<ScanResultEntity> scans = scanService.listScans(ctx.tenantId());

        return ResponseEntity.ok(
                ApiResponse.ok(scans, ctx.correlationId().toString()));
    }

    public record RunScanRequest(
            Long packId,
            String target,
            int passed,
            int failed,
            int skipped,
            String details
    ) {}
}
