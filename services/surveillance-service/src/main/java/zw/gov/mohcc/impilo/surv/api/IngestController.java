package zw.gov.mohcc.impilo.surv.api;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.surv.core.IngestService;

@RestController
@RequestMapping("/internal/v1/ingest")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<IngestService.IngestResult>> ingest(
            @RequestBody IngestRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        IngestService.IngestResult result = ingestService.ingest(
                ctx.tenantId(), ctx.actorId(), ctx.correlationId(),
                request.eventType(), request.payload(),
                request.facilityId());

        return ResponseEntity.ok(
                ApiResponse.ok(result, ctx.correlationId().toString()));
    }

    public record IngestRequest(
            String eventType,
            String payload,
            UUID facilityId
    ) {}
}
