package zw.gov.mohcc.impilo.zibo.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.zibo.api.dto.MappingRequest;
import zw.gov.mohcc.impilo.zibo.core.MappingService;

import java.util.List;

/**
 * REST controller for concept mapping operations.
 *
 * <p>Provides code-to-code mapping using the pre-built ConceptMap
 * index, and administrative endpoints for rebuilding the index.</p>
 */
@RestController
@RequestMapping("/v1/map")
public class MappingController {

    private static final Logger log = LoggerFactory.getLogger(MappingController.class);

    private final MappingService mappingService;

    public MappingController(MappingService mappingService) {
        this.mappingService = mappingService;
    }

    /**
     * Map a code from one system to another.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<MappingService.MappingResult>> mapCode(
            @Valid @RequestBody MappingRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        MappingService.MappingResult result = mappingService.mapCode(
                request.sourceSystem(),
                request.sourceCode(),
                request.targetSystem());

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }

    /**
     * Get available mapping target systems for a source system.
     */
    @GetMapping("/sources")
    public ResponseEntity<ApiResponse<List<String>>> getAvailableMappings(
            @RequestParam(required = false) String sourceSystem) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<String> mappings = mappingService.getAvailableMappings(sourceSystem);

        return ResponseEntity.ok(ApiResponse.ok(mappings, correlationId));
    }

    /**
     * Rebuild all mapping indexes (admin operation).
     */
    @PostMapping("/rebuild")
    public ResponseEntity<ApiResponse<String>> rebuildAllIndexes() {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        mappingService.rebuildAllIndexes();

        return ResponseEntity.ok(ApiResponse.ok("Mapping indexes rebuilt successfully", correlationId));
    }
}
