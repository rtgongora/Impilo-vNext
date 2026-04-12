package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.AssetRegistryServiceClient;

import java.util.UUID;

/**
 * BFF proxy for {@code asset-registry-service}.
 */
@RestController
@RequestMapping("/internal/v1/asset-registry")
public class AssetRegistrySupplyBffController {

    private final AssetRegistryServiceClient client;

    public AssetRegistrySupplyBffController(AssetRegistryServiceClient client) {
        this.client = client;
    }

    @GetMapping("/assets")
    public ResponseEntity<JsonNode> listAssets(
            @RequestParam(name = "facility_id", required = false) String facilityId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(client.listAssets(facilityId, status, cursor, limit));
    }

    @GetMapping("/assets/{assetId}")
    public ResponseEntity<JsonNode> getAsset(@PathVariable UUID assetId) {
        return ResponseEntity.ok(client.getAsset(assetId));
    }

    @GetMapping("/snapshots/assets")
    public ResponseEntity<JsonNode> snapshotAssets(
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(name = "as_of", required = false) String asOf) {
        return ResponseEntity.ok(client.snapshotAssets(cursor, limit, asOf));
    }

    @PutMapping("/assets/{assetId}")
    public ResponseEntity<JsonNode> upsertAsset(@PathVariable UUID assetId, @RequestBody JsonNode body) {
        return ResponseEntity.ok(client.upsertAsset(assetId, body));
    }

    @PutMapping("/assets/{assetId}/status")
    public ResponseEntity<JsonNode> updateStatus(@PathVariable UUID assetId, @RequestBody JsonNode body) {
        return ResponseEntity.ok(client.updateAssetStatus(assetId, body));
    }

    @DeleteMapping("/assets/{assetId}")
    public ResponseEntity<JsonNode> retireAsset(@PathVariable UUID assetId) {
        return ResponseEntity.ok(client.deleteAsset(assetId));
    }
}
