package zw.gov.mohcc.impilo.assetregistry.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.assetregistry.api.dto.AssetResponse;
import zw.gov.mohcc.impilo.assetregistry.api.dto.ExternalAssetResponse;
import zw.gov.mohcc.impilo.assetregistry.api.dto.UpsertAssetRequest;
import zw.gov.mohcc.impilo.assetregistry.core.AssetService;
import zw.gov.mohcc.impilo.assetregistry.domain.AssetEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
public class AssetController {

    private static final Logger log = LoggerFactory.getLogger(AssetController.class);
    private final AssetService assetService;
    private final ObjectMapper objectMapper;

    public AssetController(AssetService assetService, ObjectMapper objectMapper) {
        this.assetService = assetService;
        this.objectMapper = objectMapper;
    }

    @PutMapping("/internal/v1/assets/{asset_id}")
    public ResponseEntity<?> upsertAsset(
            @PathVariable("asset_id") UUID assetId,
            @Valid @RequestBody UpsertAssetRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Upsert asset [assetId={}, type={}] correlationId={}",
                assetId, request.type(), ctx.correlationId());

        AssetService.UpsertResult result = assetService.upsertAsset(
                assetId,
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                ctx.correlationId(),
                idempotencyKey,
                request);

        AssetResponse response = toAssetResponse(result.asset());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/internal/v1/assets/{asset_id}")
    public ResponseEntity<?> getAssetInternal(@PathVariable("asset_id") UUID assetId) {
        RequestContext ctx = RequestContextHolder.require();
        log.info("Get asset [assetId={}] correlationId={}", assetId, ctx.correlationId());

        Optional<AssetEntity> asset = assetService.getAsset(assetId);
        if (asset.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", "Asset not found",
                            ctx.requestId(), ctx.correlationId()));
        }
        return ResponseEntity.ok(toAssetResponse(asset.get()));
    }

    @GetMapping("/external/v1/assets/{asset_id}")
    public ResponseEntity<?> getAssetExternal(@PathVariable("asset_id") UUID assetId) {
        RequestContext ctx = RequestContextHolder.require();
        log.info("External get asset [assetId={}] correlationId={}", assetId, ctx.correlationId());

        Optional<AssetEntity> asset = assetService.getAsset(assetId);
        if (asset.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", "Asset not found",
                            ctx.requestId(), ctx.correlationId()));
        }
        AssetEntity a = asset.get();
        ExternalAssetResponse response = new ExternalAssetResponse(
                a.getAssetId(), a.getFacilityRef(), a.getType(), a.getStatus());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/internal/v1/assets/{asset_id}")
    public ResponseEntity<?> retireAsset(
            @PathVariable("asset_id") UUID assetId,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Retire asset [assetId={}] correlationId={}", assetId, ctx.correlationId());

        try {
            AssetEntity asset = assetService.retireAsset(
                    assetId,
                    UUID.fromString(ctx.tenantId()),
                    ctx.podId(),
                    ctx.correlationId(),
                    idempotencyKey);
            return ResponseEntity.ok(toAssetResponse(asset));
        } catch (AssetService.AssetNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", "Asset not found",
                            ctx.requestId(), ctx.correlationId()));
        }
    }

    @GetMapping("/internal/v1/assets")
    public ResponseEntity<?> listAssets(
            @RequestParam(name = "facility_id", required = false) String facilityId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "20") int limit) {

        RequestContext ctx = RequestContextHolder.require();
        log.info("List assets [facility_id={}, status={}, cursor={}, limit={}] correlationId={}",
                facilityId, status, cursor, limit, ctx.correlationId());

        Page<AssetEntity> page = assetService.listAssets(
                UUID.fromString(ctx.tenantId()), facilityId, status, cursor, limit);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toAssetResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("total_elements", page.getTotalElements());
        body.put("has_more", page.hasNext());
        return ResponseEntity.ok(body);
    }

    private AssetResponse toAssetResponse(AssetEntity entity) {
        Object metadata = parseJsonSafe(entity.getMetadataJson());
        return new AssetResponse(
                entity.getAssetId(),
                entity.getTenantId(),
                entity.getFacilityRef(),
                entity.getType(),
                entity.getSerialNo(),
                entity.getStatus(),
                metadata,
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private Object parseJsonSafe(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }
}
