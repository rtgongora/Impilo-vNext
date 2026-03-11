package zw.gov.mohcc.impilo.assetregistry.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.assetregistry.api.dto.AssetResponse;
import zw.gov.mohcc.impilo.assetregistry.api.dto.AssetSnapshotResponse;
import zw.gov.mohcc.impilo.assetregistry.core.AssetService;
import zw.gov.mohcc.impilo.assetregistry.domain.AssetEntity;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/internal/v1/snapshots")
public class SnapshotController {

    private static final Logger log = LoggerFactory.getLogger(SnapshotController.class);
    private final AssetService assetService;
    private final ObjectMapper objectMapper;

    public SnapshotController(AssetService assetService, ObjectMapper objectMapper) {
        this.assetService = assetService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/assets")
    public ResponseEntity<AssetSnapshotResponse> snapshotAssets(
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(name = "as_of", required = false) String asOfParam) {

        var ctx = RequestContextHolder.require();
        log.info("Snapshot assets [cursor={}, limit={}, as_of={}] correlationId={}",
                cursor, limit, asOfParam, ctx.correlationId());

        OffsetDateTime asOf = asOfParam != null
                ? OffsetDateTime.parse(asOfParam)
                : OffsetDateTime.now();

        Page<AssetEntity> page = assetService.getSnapshot(asOf, cursor, limit);

        List<AssetResponse> items = page.getContent().stream()
                .map(this::toAssetResponse)
                .toList();

        String nextCursor = page.hasNext() ? String.valueOf(cursor + 1) : null;

        AssetSnapshotResponse response = new AssetSnapshotResponse(
                items, nextCursor, limit, page.hasNext(), asOf);

        return ResponseEntity.ok(response);
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
