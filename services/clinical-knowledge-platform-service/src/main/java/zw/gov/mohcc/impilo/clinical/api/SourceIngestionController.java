package zw.gov.mohcc.impilo.clinical.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.clinical.ingestion.EdlizPdfIngestionService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Internal source ingestion — PDF chunking into {@code source_sections}. Restrict to trusted operators;
 * wire through TSHEPO / admin roles at the gateway in production.
 */
@Tag(name = "Source ingestion")
@RestController
@RequestMapping("/internal/v1/clinical/source")
public class SourceIngestionController {

    private final EdlizPdfIngestionService edlizPdfIngestionService;

    public SourceIngestionController(EdlizPdfIngestionService edlizPdfIngestionService) {
        this.edlizPdfIngestionService = edlizPdfIngestionService;
    }

    /**
     * Extract text per PDF page into {@code source_sections} with {@code section_path} prefix {@code pdf/}.
     *
     * @param body optional: {@code pdf_path} (override), {@code replace_pdf_sections} (default false),
     *             {@code verify_sha256} (default false)
     */
    @Operation(summary = "Ingest PDF into source_sections", description = "Extracts text per page, chunks long pages, stores rows under section_path prefix pdf/. "
            + "Optional body keys: pdf_path, replace_pdf_sections, verify_sha256.")
    @PostMapping("/documents/{documentId}/ingest-pdf")
    public ResponseEntity<Map<String, Object>> ingestPdf(
            @Parameter(description = "source_documents.id") @PathVariable UUID documentId,
            @RequestBody(required = false) Map<String, Object> body) {
        boolean replace = body != null && Boolean.TRUE.equals(body.get("replace_pdf_sections"));
        boolean verify = body != null && Boolean.TRUE.equals(body.get("verify_sha256"));
        String pdfPath = body != null && body.get("pdf_path") != null ? body.get("pdf_path").toString() : null;
        Map<String, Object> data = edlizPdfIngestionService.ingestPdfSections(documentId, pdfPath, replace, verify);
        return ResponseEntity.ok(Map.of("data", data));
    }

    @Operation(summary = "Ingestion summary", description = "Counts pdf/* sections vs total sections for a document.")
    @GetMapping("/documents/{documentId}/ingestion-summary")
    public ResponseEntity<Map<String, Object>> summary(
            @Parameter(description = "source_documents.id") @PathVariable UUID documentId) {
        return ResponseEntity.ok(Map.of("data", edlizPdfIngestionService.ingestionSummary(documentId)));
    }

    /**
     * Convenience: default EDLIZ 2025 seed document id from national demo migrations.
     */
    @Operation(summary = "Default seeded EDLIZ document id", description = "National demo Flyway seed row id for the EDLIZ 2025 reference bundle.")
    @GetMapping("/edliz-default-document-id")
    public ResponseEntity<Map<String, Object>> defaultEdlizDocumentId() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("document_id", "a0000001-0001-4001-8001-000000000001");
        m.put("note", "Flyway seed national guideline row; ingest-pdf targets this id unless you create another source_documents row.");
        return ResponseEntity.ok(Map.of("data", m));
    }
}
