package zw.gov.mohcc.impilo.zibo.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.zibo.api.dto.SubmitJobRequest;
import zw.gov.mohcc.impilo.zibo.api.dto.ValidateCodingRequest;
import zw.gov.mohcc.impilo.zibo.api.dto.ValidateResourceRequest;
import zw.gov.mohcc.impilo.zibo.api.dto.ValidationIssueDto;
import zw.gov.mohcc.impilo.zibo.api.dto.ValidationResultDto;
import zw.gov.mohcc.impilo.zibo.core.ValidationEngine;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ValidationJobEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for terminology validation operations.
 *
 * <p>Provides synchronous coding and resource validation, as well as
 * asynchronous batch validation via job submission.</p>
 */
@RestController
@RequestMapping("/v1/validate")
public class ValidationController {

    private static final Logger log = LoggerFactory.getLogger(ValidationController.class);

    private final ValidationEngine validationEngine;
    private final ObjectMapper objectMapper;

    public ValidationController(ValidationEngine validationEngine, ObjectMapper objectMapper) {
        this.validationEngine = validationEngine;
        this.objectMapper = objectMapper;
    }

    /**
     * Validate a single FHIR coding against a registered CodeSystem.
     */
    @PostMapping("/coding")
    public ResponseEntity<ApiResponse<ValidationResultDto>> validateCoding(
            @Valid @RequestBody ValidateCodingRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ValidationEngine.ValidationResult result = validationEngine.validateCoding(
                request.system(),
                request.code(),
                request.display(),
                request.mode(),
                request.facilityId());

        ValidationResultDto dto = toDto(result);

        return ResponseEntity.ok(ApiResponse.ok(dto, correlationId));
    }

    /**
     * Validate all codings within a FHIR resource.
     */
    @PostMapping("/resource")
    public ResponseEntity<ApiResponse<ValidationResultDto>> validateResource(
            @Valid @RequestBody ValidateResourceRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        String resourceStr = serializeContent(request.resourceJson());

        List<ValidationEngine.ValidationIssue> issues = validationEngine.validateResource(
                resourceStr, request.mode(), request.facilityId());

        boolean valid = issues.stream()
                .noneMatch(i -> i.severity() == zw.gov.mohcc.impilo.zibo.domain.ValidationSeverity.ERROR);

        List<ValidationIssueDto> issueDtos = issues.stream()
                .map(this::toIssueDto)
                .collect(Collectors.toList());

        ValidationResultDto dto = new ValidationResultDto(valid, issueDtos);

        return ResponseEntity.ok(ApiResponse.ok(dto, correlationId));
    }

    /**
     * Submit an asynchronous validation job.
     */
    @PostMapping("/job")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitJob(
            @Valid @RequestBody SubmitJobRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        String payloadStr = serializeContent(request.payloadJson());
        UUID jobId = validationEngine.submitValidationJob(payloadStr);

        Map<String, Object> response = Map.of(
                "jobId", jobId,
                "status", "PENDING"
        );

        return ResponseEntity.ok(ApiResponse.ok(response, correlationId));
    }

    /**
     * Retrieve the result of an asynchronous validation job.
     */
    @GetMapping("/job/{jobId}")
    public ResponseEntity<ApiResponse<ValidationJobEntity>> getJobResult(
            @PathVariable UUID jobId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ValidationJobEntity job = validationEngine.getJobResult(jobId);

        return ResponseEntity.ok(ApiResponse.ok(job, correlationId));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ValidationResultDto toDto(ValidationEngine.ValidationResult result) {
        List<ValidationIssueDto> issueDtos = result.issues().stream()
                .map(this::toIssueDto)
                .collect(Collectors.toList());
        return new ValidationResultDto(result.valid(), issueDtos);
    }

    private ValidationIssueDto toIssueDto(ValidationEngine.ValidationIssue issue) {
        return new ValidationIssueDto(
                issue.severity().name(),
                issue.code(),
                issue.message(),
                issue.canonicalUrl()
        );
    }

    private String serializeContent(Object content) {
        if (content instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON content", e);
        }
    }
}
