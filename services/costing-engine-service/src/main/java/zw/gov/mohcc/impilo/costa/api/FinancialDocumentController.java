package zw.gov.mohcc.impilo.costa.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.finance.GenerateFinancialDocumentRequest;
import zw.gov.mohcc.impilo.costa.domain.entity.FinancialDocumentEntity;
import zw.gov.mohcc.impilo.costa.service.FinancialDocumentService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.UUID;

@RestController
@RequestMapping("/costa/v1/finance/documents")
public class FinancialDocumentController {

    private final FinancialDocumentService financialDocumentService;

    public FinancialDocumentController(FinancialDocumentService financialDocumentService) {
        this.financialDocumentService = financialDocumentService;
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<FinancialDocumentEntity>> generate(
            @Valid @RequestBody GenerateFinancialDocumentRequest body) throws Exception {
        var ctx = TrustContextHolder.require();
        FinancialDocumentEntity doc = financialDocumentService.generate(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(doc, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<FinancialDocumentEntity>>> list(
            @RequestParam String subject_ref,
            @RequestParam(required = false) String subject_type,
            @RequestParam(required = false) String doc_type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var ctx = TrustContextHolder.require();
        var slice = financialDocumentService.listDocuments(subject_ref, subject_type, doc_type,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "generatedAt")));
        PagedResponse<FinancialDocumentEntity> paged = PagedResponse.of(
                slice.getContent(), page, size, slice.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, ctx.correlationId().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FinancialDocumentEntity>> get(@PathVariable("id") UUID docId) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(financialDocumentService.getDocument(docId),
                ctx.correlationId().toString()));
    }
}
