package zw.gov.mohcc.impilo.zibo.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.zibo.core.ExportService;
import zw.gov.mohcc.impilo.zibo.core.ImportService;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * REST controller for importing and exporting terminology artifacts.
 *
 * <p>Import supports FHIR Bundle JSON and CSV formats. Export supports
 * individual artifacts and full pack bundles.</p>
 */
@RestController
public class ImportExportController {

    private static final Logger log = LoggerFactory.getLogger(ImportExportController.class);

    private final ImportService importService;
    private final ExportService exportService;

    public ImportExportController(ImportService importService, ExportService exportService) {
        this.importService = importService;
        this.exportService = exportService;
    }

    /**
     * Import terminology artifacts from a FHIR Bundle JSON.
     */
    @PostMapping("/v1/import/fhir-bundle")
    public ResponseEntity<ApiResponse<ImportService.ImportResult>> importFhirBundle(
            @RequestBody String bundleJson) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ImportService.ImportResult result = importService.importFhirBundle(bundleJson);

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }

    /**
     * Import a CodeSystem from a CSV file.
     */
    @PostMapping(value = "/v1/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportService.ImportResult>> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam String systemUrl,
            @RequestParam String name,
            @RequestParam String version) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        try {
            Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
            ImportService.ImportResult result = importService.importCsv(systemUrl, name, version, reader);

            return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
        } catch (Exception e) {
            log.error("CSV import failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("IMPORT_FAILED", "CSV import failed: " + e.getMessage(),
                            500, correlationId));
        }
    }

    /**
     * Export a pack as a FHIR Bundle.
     */
    @GetMapping(value = "/v1/export/packs/{packId}/bundle", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> exportPackAsBundle(@PathVariable UUID packId) {
        TrustContextHolder.require();

        String bundle = exportService.exportPackAsBundle(packId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(bundle);
    }

    /**
     * Export a single artifact as its raw FHIR JSON content.
     */
    @GetMapping(value = "/v1/export/artifacts/{artifactId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> exportArtifact(@PathVariable UUID artifactId) {
        TrustContextHolder.require();

        String content = exportService.exportArtifact(artifactId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(content);
    }
}
