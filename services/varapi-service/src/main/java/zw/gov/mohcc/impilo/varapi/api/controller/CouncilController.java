package zw.gov.mohcc.impilo.varapi.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.CouncilResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.CreateCouncilRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.GenericResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.ImportTriggerRequest;
import zw.gov.mohcc.impilo.varapi.core.CouncilService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;

import java.util.List;

/**
 * Internal REST API for managing regulatory councils within the VARAPI Provider Registry.
 *
 * Councils are the bodies that license and regulate healthcare providers
 * (e.g., MDPCZ, NMCZ, AHPCZ). This controller manages council CRUD
 * and data import triggers from council systems.
 *
 * All endpoints require a valid trust context provided by TSHEPO ext_authz.
 */
@RestController
@RequestMapping("/v1/internal/councils")
public class CouncilController {

    private static final Logger log = LoggerFactory.getLogger(CouncilController.class);

    private final CouncilService councilService;

    public CouncilController(CouncilService councilService) {
        this.councilService = councilService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CouncilResponse>> createCouncil(
            @Valid @RequestBody CreateCouncilRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating council [code={}, name={}] correlationId={}",
                request.councilCode(), request.name(), ctx.correlationId());

        CouncilEntity entity = councilService.createCouncil(request);
        CouncilResponse response = toCouncilResponse(entity);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/{councilId}")
    public ResponseEntity<ApiResponse<CouncilResponse>> getCouncil(
            @PathVariable Long councilId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching council [councilId={}] correlationId={}", councilId, ctx.correlationId());

        CouncilEntity entity = councilService.getCouncil(councilId);
        CouncilResponse response = toCouncilResponse(entity);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CouncilResponse>>> listCouncils() {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Listing all councils correlationId={}", ctx.correlationId());

        List<CouncilEntity> entities = councilService.listCouncils();
        List<CouncilResponse> responses = entities.stream()
                .map(this::toCouncilResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(responses, ctx.correlationId().toString()));
    }

    @PostMapping("/{councilId}/imports")
    public ResponseEntity<ApiResponse<GenericResponse>> triggerImport(
            @PathVariable Long councilId,
            @Valid @RequestBody ImportTriggerRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Triggering import [councilId={}, importType={}] correlationId={}",
                councilId, request.importType(), ctx.correlationId());

        councilService.triggerImport(councilId, request);
        GenericResponse response = new GenericResponse("ACCEPTED",
                "Import job queued for council " + councilId);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    // ---- Mapper: Entity → Response DTO ----

    private CouncilResponse toCouncilResponse(CouncilEntity entity) {
        return new CouncilResponse(
                entity.getId(),
                entity.getCouncilCode(),
                entity.getName(),
                entity.getCouncilType(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getWebsite(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
