package zw.gov.mohcc.impilo.msika.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import zw.gov.mohcc.impilo.msika.api.dto.ImportSourceRequest;
import zw.gov.mohcc.impilo.msika.core.ImportService;
import zw.gov.mohcc.impilo.msika.persistence.entity.ExternalSourceEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.ImportJobEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

@RestController
@RequestMapping("/v1/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    @PostMapping("/csv")
    public ResponseEntity<ApiResponse<ImportJobEntity>> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("catalogId") String catalogId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ImportJobEntity job = importService.importCsv(file, catalogId);
        return ResponseEntity.ok(ApiResponse.ok(job, correlationId));
    }

    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    @PostMapping("/sources")
    public ResponseEntity<ApiResponse<ExternalSourceEntity>> createSource(
            @Valid @RequestBody ImportSourceRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ExternalSourceEntity source = importService.createSource(request);
        return ResponseEntity.ok(ApiResponse.ok(source, correlationId));
    }

    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    @PostMapping("/sources/{sourceId}/run")
    public ResponseEntity<ApiResponse<ImportJobEntity>> runSourceImport(@PathVariable String sourceId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ImportJobEntity job = importService.runSourceImport(sourceId);
        return ResponseEntity.ok(ApiResponse.ok(job, correlationId));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<ImportJobEntity>> getJob(@PathVariable String jobId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ImportJobEntity job = importService.getJob(jobId);
        return ResponseEntity.ok(ApiResponse.ok(job, correlationId));
    }
}
