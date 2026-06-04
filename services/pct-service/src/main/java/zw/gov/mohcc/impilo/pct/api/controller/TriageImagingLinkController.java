package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.persistence.entity.TriageImagingLinkEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.TriageImagingLinkRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/triage-imaging-links")
public class TriageImagingLinkController {
    private final TriageImagingLinkRepository repository;

    public TriageImagingLinkController(TriageImagingLinkRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TriageImagingLinkEntity>> create(@RequestBody Map<String, Object> body) {
        TrustContext trust = TrustContextHolder.require();
        TriageImagingLinkEntity row = new TriageImagingLinkEntity();
        row.setTenantId(trust.tenantId());
        row.setJourneyId((String) body.get("journey_id"));
        row.setTriageRecordId(toLong(body.get("triage_record_id")));
        row.setStudyId(toLong(body.get("study_id")));
        row.setSeriesId(toLong(body.get("series_id")));
        row.setInstanceId(toLong(body.get("instance_id")));
        row.setImagingEditId(toLong(body.get("imaging_edit_id")));
        row.setCreatedBy((String) body.getOrDefault("created_by", trust.actorId()));
        TriageImagingLinkEntity saved = repository.save(row);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(saved, trust.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TriageImagingLinkEntity>>> list(
            @RequestParam(name = "journey_id", required = false) String journeyId,
            @RequestParam(name = "study_id", required = false) Long studyId) {
        TrustContext trust = TrustContextHolder.require();
        List<TriageImagingLinkEntity> rows;
        if (journeyId != null && !journeyId.isBlank()) {
            rows = repository.findByTenantIdAndJourneyIdOrderByCreatedAtDesc(trust.tenantId(), journeyId);
        } else if (studyId != null) {
            rows = repository.findByTenantIdAndStudyIdOrderByCreatedAtDesc(trust.tenantId(), studyId);
        } else {
            rows = List.of();
        }
        return ResponseEntity.ok(ApiResponse.ok(rows, trust.correlationId().toString()));
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        return Long.parseLong(String.valueOf(value));
    }
}
