package zw.gov.mohcc.impilo.ndr.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.ndr.api.dto.QueryRequest;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/query")
public class QueryController {

    @PostMapping
    public ResponseEntity<ErrorEnvelope> query(@Valid @RequestBody QueryRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID.fromString(ctx.tenantId());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorEnvelope.of(
                        "NDR_RUNTIME_OWNER_CONFLICT",
                        "Runtime query ownership moved to ndr-service. Use /internal/v1/ndr/query/* endpoints.",
                        Map.of("runtime_owner", "ndr-service", "requested_dataset_key", request.datasetKey()),
                        ctx.requestId(),
                        ctx.correlationId()));
    }
}
