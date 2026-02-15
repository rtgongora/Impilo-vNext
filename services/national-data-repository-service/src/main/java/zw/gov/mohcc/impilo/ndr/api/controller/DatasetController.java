package zw.gov.mohcc.impilo.ndr.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.ndr.api.dto.CreateDatasetRequest;
import zw.gov.mohcc.impilo.ndr.api.dto.CreateVersionRequest;
import zw.gov.mohcc.impilo.ndr.core.DatasetService;
import zw.gov.mohcc.impilo.ndr.persistence.entity.DatasetEntity;
import zw.gov.mohcc.impilo.ndr.persistence.entity.DatasetVersionEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/datasets")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @PostMapping
    public ResponseEntity<?> createDataset(@Valid @RequestBody CreateDatasetRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        try {
            DatasetEntity dataset = datasetService.createDataset(
                    tenantId,
                    request.datasetKey(),
                    request.name(),
                    request.description(),
                    request.owner()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(dataset);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    new ErrorEnvelope(new ErrorEnvelope.ErrorBody(
                            "DATASET_KEY_EXISTS",
                            e.getMessage(),
                            Map.of("dataset_key", request.datasetKey()),
                            ctx.requestId(),
                            ctx.correlationId()
                    ))
            );
        }
    }

    @PostMapping("/{key}/versions")
    public ResponseEntity<?> createVersion(@PathVariable String key,
                                           @Valid @RequestBody CreateVersionRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        try {
            DatasetVersionEntity version = datasetService.createVersion(
                    tenantId, key, request.schemaJson(), request.rowCount()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(version);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new ErrorEnvelope(new ErrorEnvelope.ErrorBody(
                            "DATASET_NOT_FOUND",
                            e.getMessage(),
                            Map.of("dataset_key", key),
                            ctx.requestId(),
                            ctx.correlationId()
                    ))
            );
        }
    }

    @GetMapping
    public ResponseEntity<List<DatasetEntity>> listDatasets() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<DatasetEntity> datasets = datasetService.listDatasets(tenantId);
        return ResponseEntity.ok(datasets);
    }
}
