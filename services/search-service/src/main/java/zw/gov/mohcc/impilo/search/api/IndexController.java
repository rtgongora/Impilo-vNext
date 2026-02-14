package zw.gov.mohcc.impilo.search.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthority;
import zw.gov.mohcc.impilo.search.api.dto.IndexDefinitionRequest;
import zw.gov.mohcc.impilo.search.api.dto.IndexDefinitionResponse;
import zw.gov.mohcc.impilo.search.service.IndexDefinitionService;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/indexes")
public class IndexController {

    private final IndexDefinitionService indexService;

    public IndexController(IndexDefinitionService indexService) {
        this.indexService = indexService;
    }

    @PostMapping
    public ResponseEntity<IndexDefinitionResponse> create(
            @Valid @RequestBody IndexDefinitionRequest request) {
        FederationAuthority.requireNational();
        RequestContext ctx = RequestContextHolder.require();
        IndexDefinitionResponse response = indexService.create(request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IndexDefinitionResponse> getById(@PathVariable String id) {
        RequestContextHolder.require();
        IndexDefinitionResponse response = indexService.getById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<IndexDefinitionResponse>> list() {
        RequestContext ctx = RequestContextHolder.require();
        List<IndexDefinitionResponse> indexes = indexService.listByTenant(ctx.tenantId());
        return ResponseEntity.ok(indexes);
    }

    @PutMapping("/{id}")
    public ResponseEntity<IndexDefinitionResponse> update(
            @PathVariable String id,
            @Valid @RequestBody IndexDefinitionRequest request) {
        FederationAuthority.requireNational();
        RequestContext ctx = RequestContextHolder.require();
        IndexDefinitionResponse response = indexService.update(id, request, ctx);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        FederationAuthority.requireNational();
        RequestContext ctx = RequestContextHolder.require();
        indexService.delete(id, ctx);
        return ResponseEntity.noContent().build();
    }
}
