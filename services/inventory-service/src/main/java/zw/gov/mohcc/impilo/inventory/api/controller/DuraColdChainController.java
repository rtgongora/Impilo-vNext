package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.*;
import zw.gov.mohcc.impilo.inventory.core.ColdChainService;
import zw.gov.mohcc.impilo.inventory.domain.ExcursionStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ColdChainExcursionEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ColdChainLocationEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dura cold-chain REST API ({@code /v1/dura/cold-chain}).
 */
@RestController
@RequestMapping("/v1/dura/cold-chain")
public class DuraColdChainController {

    private static final Logger log = LoggerFactory.getLogger(DuraColdChainController.class);

    private final ColdChainService coldChainService;

    public DuraColdChainController(ColdChainService coldChainService) {
        this.coldChainService = coldChainService;
    }

    @PostMapping("/locations")
    public ResponseEntity<ApiResponse<ColdChainLocationDto>> registerLocation(
            @Valid @RequestBody RegisterColdChainLocationRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ColdChainLocationEntity location = coldChainService.registerLocation(
                request.facilityId(), request.storeId(), request.name(),
                request.type(), request.tempMin(), request.tempMax());
        log.info("Cold-chain location registered via API: name={}", request.name());
        return ResponseEntity.ok(ApiResponse.ok(ColdChainLocationDto.from(location), correlationId));
    }

    @GetMapping("/locations")
    public ResponseEntity<ApiResponse<List<ColdChainLocationDto>>> listLocations(
            @RequestParam(value = "facilityId", required = false) UUID facilityId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<ColdChainLocationDto> dtos = coldChainService.listLocations(facilityId).stream()
                .map(ColdChainLocationDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @GetMapping("/locations/{ccLocationId}")
    public ResponseEntity<ApiResponse<ColdChainLocationDto>> getLocation(@PathVariable UUID ccLocationId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                ColdChainLocationDto.from(coldChainService.getLocation(ccLocationId)), correlationId));
    }

    @PostMapping("/locations/{ccLocationId}/temperature")
    public ResponseEntity<ApiResponse<LogTemperatureResponse>> logTemperature(
            @PathVariable UUID ccLocationId,
            @Valid @RequestBody LogTemperatureRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ColdChainService.LogResult result = coldChainService.logTemperature(
                ccLocationId, request.temperature(), request.source(), request.recordedAt());
        return ResponseEntity.ok(ApiResponse.ok(LogTemperatureResponse.from(result), correlationId));
    }

    @GetMapping("/locations/{ccLocationId}/logs")
    public ResponseEntity<ApiResponse<List<TemperatureLogDto>>> recentLogs(
            @PathVariable UUID ccLocationId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<TemperatureLogDto> dtos = coldChainService.recentLogs(ccLocationId, limit).stream()
                .map(TemperatureLogDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @GetMapping("/excursions")
    public ResponseEntity<ApiResponse<List<ColdChainExcursionDto>>> listExcursions(
            @RequestParam(value = "status", required = false) ExcursionStatus status) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<ColdChainExcursionDto> dtos = coldChainService.listExcursions(status).stream()
                .map(ColdChainExcursionDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @PostMapping("/excursions/{excursionId}/resolve")
    public ResponseEntity<ApiResponse<ColdChainExcursionDto>> resolveExcursion(
            @PathVariable UUID excursionId,
            @Valid @RequestBody ResolveExcursionRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ColdChainExcursionEntity excursion = coldChainService.resolveExcursion(
                excursionId, request.resolution(), request.notes());
        return ResponseEntity.ok(ApiResponse.ok(ColdChainExcursionDto.from(excursion), correlationId));
    }
}
