package zw.gov.mohcc.impilo.search.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.search.core.SearchIndexService;

@RestController
@RequestMapping("/internal/v1/search")
public class SearchIndexController {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexController.class);

    private final SearchIndexService searchIndexService;

    public SearchIndexController(SearchIndexService searchIndexService) {
        this.searchIndexService = searchIndexService;
    }

    @PostMapping("/index")
    public ResponseEntity<IndexResponse> indexEntity(@Valid @RequestBody IndexRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        log.info("Index entity [entityType={}, entityId={}] correlationId={}",
                request.entityType(), request.entityId(), ctx.correlationId());

        IndexResponse response = searchIndexService.indexEntity(request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/index/{entityType}/{entityId}")
    public ResponseEntity<Void> removeEntity(@PathVariable String entityType,
                                              @PathVariable String entityId) {
        RequestContext ctx = RequestContextHolder.require();
        log.info("Remove entity from index [entityType={}, entityId={}] correlationId={}",
                entityType, entityId, ctx.correlationId());

        searchIndexService.removeEntity(entityType, entityId, ctx);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @RequestParam("q") String query,
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        RequestContext ctx = RequestContextHolder.require();
        log.info("Search [query={}, entityType={}, page={}, size={}] tenantId={}",
                query, entityType, page, size, ctx.tenantId());

        SearchResponse response = searchIndexService.search(query, ctx.tenantId(), entityType, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/index/{entityType}")
    public ResponseEntity<SearchResponse> listByType(
            @PathVariable String entityType,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        RequestContext ctx = RequestContextHolder.require();
        log.info("List by type [entityType={}, page={}, size={}] tenantId={}",
                entityType, page, size, ctx.tenantId());

        SearchResponse response = searchIndexService.listByType(entityType, ctx.tenantId(), page, size);
        return ResponseEntity.ok(response);
    }
}
