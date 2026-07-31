package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.pct.core.clinical.MedicationReconciliationService;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicationReconciliationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicationReconciliationItemEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read surface for V107 medication reconciliation — exposes existing SoR for clerking compose.
 * Writes remain on the service; this wave only needs findings (e.g. NOT_TAKING) on the meds panel.
 */
@RestController
@RequestMapping("/v1/medication-reconciliations")
public class MedicationReconciliationController {

    private final MedicationReconciliationService reconciliationService;

    public MedicationReconciliationController(MedicationReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        List<Map<String, Object>> out = reconciliationService.list(subjectCpid).stream()
                .map(this::toMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @GetMapping("/{reconciliationId}/items")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> items(
            @PathVariable UUID reconciliationId) {
        List<Map<String, Object>> out = reconciliationService.items(reconciliationId).stream()
                .map(this::itemToMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }

    private Map<String, Object> toMap(MedicationReconciliationEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reconciliation_id", e.getReconciliationId().toString());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("journey_id", e.getJourneyId());
        m.put("encounter_id", e.getEncounterId());
        m.put("episode_id", e.getEpisodeId() == null ? null : e.getEpisodeId().toString());
        m.put("context", e.getContext());
        m.put("status", e.getStatus());
        m.put("started_at", e.getStartedAt() != null ? e.getStartedAt().toString() : null);
        m.put("completed_at", e.getCompletedAt() != null ? e.getCompletedAt().toString() : null);
        return m;
    }

    private Map<String, Object> itemToMap(MedicationReconciliationItemEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("item_id", e.getItemId().toString());
        m.put("reconciliation_id", e.getReconciliationId().toString());
        m.put("display", e.getDisplay());
        m.put("code", e.getCode());
        m.put("system_says", e.getSystemSays());
        m.put("patient_says", e.getPatientSays());
        m.put("finding", e.getFinding());
        m.put("action", e.getAction());
        m.put("source", e.getSource());
        m.put("notes", e.getNotes());
        return m;
    }
}
