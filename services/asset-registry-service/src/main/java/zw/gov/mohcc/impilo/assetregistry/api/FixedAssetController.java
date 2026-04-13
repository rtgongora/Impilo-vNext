package zw.gov.mohcc.impilo.assetregistry.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.assetregistry.core.FixedAssetService;
import zw.gov.mohcc.impilo.assetregistry.domain.AssetDepreciationScheduleEntity;
import zw.gov.mohcc.impilo.assetregistry.domain.AssetFixedDetailEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/fixed-assets")
public class FixedAssetController {

    private final FixedAssetService fixedAssetService;

    public FixedAssetController(FixedAssetService fixedAssetService) {
        this.fixedAssetService = fixedAssetService;
    }

    @GetMapping("/assets/{assetId}/details")
    public ResponseEntity<AssetFixedDetailEntity> details(@PathVariable UUID assetId) {
        AssetFixedDetailEntity d = fixedAssetService.getOrNull(assetId);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }

    @PutMapping("/assets/{assetId}/details")
    public AssetFixedDetailEntity upsert(@PathVariable UUID assetId, @RequestBody AssetFixedDetailEntity body) {
        body.setAssetId(assetId);
        return fixedAssetService.upsert(body);
    }

    @PostMapping("/assets/{assetId}/depreciation/run")
    public AssetDepreciationScheduleEntity runDep(@PathVariable UUID assetId, @RequestBody Map<String, String> body) {
        LocalDate pd = LocalDate.parse(body.getOrDefault("periodDate", LocalDate.now().toString()));
        return fixedAssetService.runMonthlyDepreciation(assetId, pd);
    }

    @GetMapping("/assets/{assetId}/depreciation/schedule")
    public List<AssetDepreciationScheduleEntity> sched(@PathVariable UUID assetId) {
        return fixedAssetService.schedule(assetId);
    }

    @PostMapping("/assets/{assetId}/disposal")
    public AssetFixedDetailEntity dispose(@PathVariable UUID assetId, @RequestBody Map<String, Object> body) {
        return fixedAssetService.recordDisposal(
                assetId,
                LocalDate.parse(body.get("disposalDate").toString()),
                new java.math.BigDecimal(body.get("disposalAmount").toString()),
                body.get("reason") != null ? body.get("reason").toString() : null
        );
    }
}
