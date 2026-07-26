package zw.gov.mohcc.impilo.zibo.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.zibo.core.ConfidentialCategoryService;

import java.util.List;
import java.util.UUID;

/**
 * The governed answer to "is this clinical content confidential by nature?".
 *
 * <p>Clinical systems of record call this when writing a record, and stamp the returned sensitivity
 * class on it. The point of centralising it is that what counts as confidential becomes a reviewable
 * governed value set (zibo migration {@code V008}) rather than a judgement duplicated across every
 * clinical service, where nobody can review it and no two copies agree.</p>
 *
 * <p>Internal lane: called service-to-service, never from a browser. Classification is about content,
 * not about a person — no PII crosses this boundary, only code system + code.</p>
 */
@RestController
@RequestMapping("/internal/v1/confidentiality")
public class ConfidentialityClassificationController {

    private final ConfidentialCategoryService confidentialCategoryService;

    public ConfidentialityClassificationController(ConfidentialCategoryService confidentialCategoryService) {
        this.confidentialCategoryService = confidentialCategoryService;
    }

    /** One coded clinical entry to classify. */
    public record CodedEntryRequest(String system, String code) {}

    /** The codes on the record being written. */
    public record ClassifyRequest(@NotEmpty List<CodedEntryRequest> codes) {}

    @PostMapping("/classify")
    public ResponseEntity<ApiResponse<ConfidentialCategoryService.ClassificationResult>> classify(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @Valid @RequestBody ClassifyRequest request) {

        List<ConfidentialCategoryService.CodedEntry> entries = request.codes().stream()
                .map(c -> new ConfidentialCategoryService.CodedEntry(c.system(), c.code()))
                .toList();

        ConfidentialCategoryService.ClassificationResult result =
                confidentialCategoryService.classify(UUID.fromString(tenantId), entries);

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }

    /**
     * The governed category vocabulary currently in force. For governance review surfaces — a list
     * nobody can read is not reviewable, and reviewability is the whole reason it lives here.
     */
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> categories(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Correlation-ID") String correlationId) {

        return ResponseEntity.ok(ApiResponse.ok(
                confidentialCategoryService.activeCategories(UUID.fromString(tenantId)), correlationId));
    }
}
