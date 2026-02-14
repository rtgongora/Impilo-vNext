package zw.gov.mohcc.impilo.search.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.search.api.dto.DocumentResponse;
import zw.gov.mohcc.impilo.search.api.dto.DocumentUpsertRequest;
import zw.gov.mohcc.impilo.search.service.DocumentService;

@RestController
@RequestMapping("/internal/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> upsert(
            @Valid @RequestBody DocumentUpsertRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        DocumentResponse response = documentService.upsert(request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> delete(@PathVariable String docId) {
        RequestContext ctx = RequestContextHolder.require();
        documentService.delete(docId, ctx);
        return ResponseEntity.noContent().build();
    }
}
