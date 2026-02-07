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
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.zibo.api.dto.AddPackArtifactRequest;
import zw.gov.mohcc.impilo.zibo.api.dto.ArtifactSummaryDto;
import zw.gov.mohcc.impilo.zibo.api.dto.CreatePackRequest;
import zw.gov.mohcc.impilo.zibo.api.dto.PackSummaryDto;
import zw.gov.mohcc.impilo.zibo.core.PackService;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.PackArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.PackEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.PackArtifactRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for managing terminology packs.
 *
 * <p>Packs are curated collections of FHIR artifacts that can be
 * assigned to scopes (tenant, facility, workspace) for governance.</p>
 */
@RestController
@RequestMapping("/v1/packs")
public class PackController {

    private static final Logger log = LoggerFactory.getLogger(PackController.class);

    private final PackService packService;
    private final PackArtifactRepository packArtifactRepository;
    private final ObjectMapper objectMapper;

    public PackController(PackService packService,
                          PackArtifactRepository packArtifactRepository,
                          ObjectMapper objectMapper) {
        this.packService = packService;
        this.packArtifactRepository = packArtifactRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new terminology pack.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PackEntity>> createPack(
            @Valid @RequestBody CreatePackRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        String manifestStr = request.manifestJson() != null
                ? serializeContent(request.manifestJson())
                : null;

        PackEntity pack = packService.createPack(
                request.name(),
                request.version(),
                request.description(),
                manifestStr);

        return ResponseEntity.ok(ApiResponse.ok(pack, correlationId));
    }

    /**
     * Add an artifact to a pack.
     */
    @PostMapping("/{id}/artifacts")
    public ResponseEntity<ApiResponse<PackArtifactEntity>> addArtifact(
            @PathVariable UUID id,
            @Valid @RequestBody AddPackArtifactRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        PackArtifactEntity result = packService.addArtifact(id, request.artifactId());

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }

    /**
     * Remove an artifact from a pack.
     */
    @DeleteMapping("/{id}/artifacts/{artifactId}")
    public ResponseEntity<ApiResponse<String>> removeArtifact(
            @PathVariable UUID id,
            @PathVariable UUID artifactId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        packService.removeArtifact(id, artifactId);

        return ResponseEntity.ok(ApiResponse.ok("Artifact removed from pack", correlationId));
    }

    /**
     * Publish a pack, making it available for assignment.
     */
    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<PackEntity>> publishPack(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        PackEntity pack = packService.publishPack(id);

        return ResponseEntity.ok(ApiResponse.ok(pack, correlationId));
    }

    /**
     * Deprecate a published pack.
     */
    @PostMapping("/{id}/deprecate")
    public ResponseEntity<ApiResponse<PackEntity>> deprecatePack(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        PackEntity pack = packService.deprecatePack(id);

        return ResponseEntity.ok(ApiResponse.ok(pack, correlationId));
    }

    /**
     * Retrieve a single pack by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PackEntity>> getPack(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        PackEntity pack = packService.getPack(id);

        return ResponseEntity.ok(ApiResponse.ok(pack, correlationId));
    }

    /**
     * Retrieve all artifacts in a pack.
     */
    @GetMapping("/{id}/artifacts")
    public ResponseEntity<ApiResponse<List<ArtifactSummaryDto>>> getPackArtifacts(
            @PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<ArtifactEntity> artifacts = packService.getPackArtifacts(id);
        List<ArtifactSummaryDto> dtos = artifacts.stream()
                .map(ArtifactSummaryDto::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    /**
     * List packs with optional filters and pagination.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<PackSummaryDto>>> listPacks(
            @RequestParam(required = false) ArtifactStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        Page<PackEntity> result = packService.listPacks(PageRequest.of(page, size));
        List<PackSummaryDto> dtos = result.getContent().stream()
                .map(p -> PackSummaryDto.from(p, packArtifactRepository.findByPackId(p.getPackId()).size()))
                .collect(Collectors.toList());
        PagedResponse<PackSummaryDto> paged = PagedResponse.of(
                dtos, page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }

    private String serializeContent(Object content) {
        if (content instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid manifest JSON", e);
        }
    }
}
