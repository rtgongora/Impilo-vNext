package zw.gov.mohcc.impilo.datawarehouse.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.datawarehouse.api.dto.*;
import zw.gov.mohcc.impilo.datawarehouse.core.GoldMaterializerService;
import zw.gov.mohcc.impilo.datawarehouse.domain.*;
import zw.gov.mohcc.impilo.datawarehouse.repository.*;

import java.util.*;

@RestController
public class GoldQueryController {

    private static final Logger log = LoggerFactory.getLogger(GoldQueryController.class);
    private static final int MAX_LIMIT = 200;

    private final GoldEncounterRepository encounterRepository;
    private final GoldMedicationRepository medicationRepository;
    private final GoldLabRepository labRepository;
    private final GoldMaterializerService materializerService;

    public GoldQueryController(GoldEncounterRepository encounterRepository,
                                GoldMedicationRepository medicationRepository,
                                GoldLabRepository labRepository,
                                GoldMaterializerService materializerService) {
        this.encounterRepository = encounterRepository;
        this.medicationRepository = medicationRepository;
        this.labRepository = labRepository;
        this.materializerService = materializerService;
    }

    // ── GET /internal/v1/gold/query — Query gold tables ──

    @GetMapping("/internal/v1/gold/query")
    public ResponseEntity<?> queryGold(
            @RequestParam String dataset,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int limit) {

        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        Pageable pageable = PageRequest.of(page, effectiveLimit, Sort.by("id").ascending());

        log.info("Gold query [dataset={}, page={}, limit={}] correlationId={}",
                dataset, page, effectiveLimit, ctx.correlationId());

        return switch (dataset) {
            case "encounters" -> {
                Page<GoldEncounterEntity> results = encounterRepository.findAll(pageable);
                yield ResponseEntity.ok(new GoldQueryResponse(dataset, results.getContent(),
                        results.hasNext() ? String.valueOf(page + 1) : null,
                        results.getTotalElements()));
            }
            case "medications" -> {
                Page<GoldMedicationEntity> results = medicationRepository.findAll(pageable);
                yield ResponseEntity.ok(new GoldQueryResponse(dataset, results.getContent(),
                        results.hasNext() ? String.valueOf(page + 1) : null,
                        results.getTotalElements()));
            }
            case "labs" -> {
                Page<GoldLabEntity> results = labRepository.findAll(pageable);
                yield ResponseEntity.ok(new GoldQueryResponse(dataset, results.getContent(),
                        results.hasNext() ? String.valueOf(page + 1) : null,
                        results.getTotalElements()));
            }
            default -> ResponseEntity.badRequest().body(Map.of("error", Map.of(
                    "code", "UNKNOWN_DATASET",
                    "message", "Unknown dataset: " + dataset + ". Available: encounters, medications, labs",
                    "details", Map.of(),
                    "request_id", ctx.requestId(),
                    "correlation_id", ctx.correlationId())));
        };
    }

    // ── GET /internal/v1/gold/stats — Materializer stats ──

    @GetMapping("/internal/v1/gold/stats")
    public ResponseEntity<?> stats() {
        RequestContextHolder.require();
        return ResponseEntity.ok(materializerService.getStats());
    }

    // ── GET /external/v1/gold/datasets — List available datasets and schemas ──

    @GetMapping("/external/v1/gold/datasets")
    public ResponseEntity<?> listDatasets() {
        RequestContextHolder.require();

        List<GoldDatasetInfo> datasets = List.of(
                new GoldDatasetInfo("encounters", "Gold Encounters",
                        "Materialized encounter records from clinical events",
                        List.of("tenant_id", "encounter_id", "patient_id", "facility_id",
                                "encounter_type", "encounter_status", "start_date", "end_date",
                                "provider_id", "diagnosis_codes")),
                new GoldDatasetInfo("medications", "Gold Medications",
                        "Materialized medication/prescription records",
                        List.of("tenant_id", "medication_id", "patient_id", "encounter_id",
                                "facility_id", "drug_code", "drug_name", "dosage",
                                "frequency", "prescribed_date", "dispensed_date")),
                new GoldDatasetInfo("labs", "Gold Lab Results",
                        "Materialized laboratory observation records",
                        List.of("tenant_id", "lab_result_id", "patient_id", "encounter_id",
                                "facility_id", "test_code", "test_name", "result_value",
                                "result_unit", "result_status", "collected_date", "reported_date"))
        );

        return ResponseEntity.ok(datasets);
    }
}
