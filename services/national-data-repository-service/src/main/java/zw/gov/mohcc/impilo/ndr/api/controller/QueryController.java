package zw.gov.mohcc.impilo.ndr.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.ndr.api.dto.QueryRequest;
import zw.gov.mohcc.impilo.ndr.api.dto.QueryResponse;
import zw.gov.mohcc.impilo.ndr.core.QueryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/query")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        List<String> rows = queryService.query(
                tenantId, request.datasetKey(), request.filters()
        );

        return ResponseEntity.ok(new QueryResponse(
                request.datasetKey(),
                rows.size(),
                rows
        ));
    }
}
