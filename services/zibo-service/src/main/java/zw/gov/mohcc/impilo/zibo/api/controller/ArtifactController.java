package zw.gov.mohcc.impilo.zibo.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.zibo.api.dto.ArtifactSummaryDto;
import zw.gov.mohcc.impilo.zibo.api.dto.CreateArtifactRequest;
import zw.gov.mohcc.impilo.zibo.api.dto.UpdateArtifactRequest;
import zw.gov.mohcc.impilo.zibo.core.ArtifactService;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for managing FHIR terminology artifacts.
 *
 * <p>Provides CRUD operations and lifecycle transitions (draft, publish,
 * deprecate, retire) for terminology artifacts. All endpoints are
 * tenant-scoped via the trust context.</p>
 */
@RestController
@RequestMapping("/v1/artifacts")
public class ArtifactController {

    private static final Logger log = LoggerFactory.getLogger(ArtifactController.class);

    private final ArtifactService artifactService;
    private final ObjectMapper objectMapper;

    public ArtifactController(ArtifactService artifactService, ObjectMapper objectMapper) {
        this.artifactService = artifactService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new draft terminology artifact.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ArtifactEntity>> createDraft(
            @Valid @RequestBody CreateArtifactRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        String contentStr = serializeContent(request.contentJson());

        ArtifactEntity artifact = artifactService.createDraft(
                request.fhirType(),
                request.canonicalUrl(),
                request.version(),
                request.name(),
                request.title(),
                request.description(),
                contentStr,
                request.publisher());

        return ResponseEntity.ok(ApiResponse.ok(artifact, correlationId));
    }

    /**
     * Update a draft artifact's mutable fields.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ArtifactEntity>> updateDraft(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateArtifactRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        String contentStr = request.contentJson() != null
                ? serializeContent(request.contentJson())
                : null;

        ArtifactEntity artifact = artifactService.updateDraft(
                id,
                request.name(),
                request.title(),
                request.description(),
                contentStr);

        return ResponseEntity.ok(ApiResponse.ok(artifact, correlationId));
    }

    /**
     * Publish a draft artifact, making it immutable and available for use.
     */
    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<ArtifactEntity>> publish(@PathVariable UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        ArtifactEntity artifact = artifactService.publish(id, ctx.actorId());

        return ResponseEntity.ok(ApiResponse.ok(artifact, correlationId));
    }

    /**
     * Deprecate a published artifact.
     */
    @PostMapping("/{id}/deprecate")
    public ResponseEntity<ApiResponse<ArtifactEntity>> deprecate(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ArtifactEntity artifact = artifactService.deprecate(id);

        return ResponseEntity.ok(ApiResponse.ok(artifact, correlationId));
    }

    /**
     * Retire a deprecated artifact.
     */
    @PostMapping("/{id}/retire")
    public ResponseEntity<ApiResponse<ArtifactEntity>> retire(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ArtifactEntity artifact = artifactService.retire(id);

        return ResponseEntity.ok(ApiResponse.ok(artifact, correlationId));
    }

    /**
     * Retrieve a single artifact by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ArtifactEntity>> getArtifact(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ArtifactEntity artifact = artifactService.getArtifact(id);

        return ResponseEntity.ok(ApiResponse.ok(artifact, correlationId));
    }

    /**
     * List artifacts with optional filters and pagination.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ArtifactSummaryDto>>> listArtifacts(
            @RequestParam(required = false) ArtifactType fhirType,
            @RequestParam(required = false) ArtifactStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        if (fhirType != null) {
            List<ArtifactEntity> artifacts = artifactService.listByType(fhirType);
            List<ArtifactSummaryDto> dtos = artifacts.stream()
                    .map(ArtifactSummaryDto::from)
                    .collect(Collectors.toList());
            PagedResponse<ArtifactSummaryDto> paged = PagedResponse.of(dtos, 0, dtos.size(), dtos.size());
            return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
        }

        Page<ArtifactEntity> result = artifactService.listArtifacts(PageRequest.of(page, size));
        List<ArtifactSummaryDto> dtos = result.getContent().stream()
                .map(ArtifactSummaryDto::from)
                .collect(Collectors.toList());
        PagedResponse<ArtifactSummaryDto> paged = PagedResponse.of(
                dtos, page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }

    /**
     * List all versions of an artifact by canonical URL.
     */
    @GetMapping("/versions")
    public ResponseEntity<ApiResponse<List<ArtifactSummaryDto>>> listVersions(
            @RequestParam String canonicalUrl) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<ArtifactEntity> versions = artifactService.listVersions(canonicalUrl);
        List<ArtifactSummaryDto> dtos = versions.stream()
                .map(ArtifactSummaryDto::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    /**
     * Resolve an artifact by its canonical URL and version.
     */
    @GetMapping("/resolve")
    public ResponseEntity<ApiResponse<ArtifactEntity>> getByCanonicalAndVersion(
            @RequestParam String canonicalUrl,
            @RequestParam String version) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ArtifactEntity artifact = artifactService.getByCanonicalAndVersion(canonicalUrl, version);

        return ResponseEntity.ok(ApiResponse.ok(artifact, correlationId));
    }

    private String serializeContent(Object content) {
        if (content instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid content JSON", e);
        }
    }
}
