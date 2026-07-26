package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.pct.core.clinical.ImmunizationService;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImmunizationEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Immunisation dose SoR API. Fulfils the BFF strangler contract that previously 404'd
 * ({@code GET/POST /v1/immunizations}), so recorded vaccinations stop being lost.
 *
 * <p>Field names are snake_case to match the contract the BFF already sends.</p>
 */
@RestController
@RequestMapping("/v1/immunizations")
public class ImmunizationController {

    private final ImmunizationService immunizationService;

    public ImmunizationController(ImmunizationService immunizationService) {
        this.immunizationService = immunizationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(name = "patient_id", required = false) String patientId,
            @RequestParam(name = "subject_cpid", required = false) String subjectCpid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String subject = subjectCpid != null && !subjectCpid.isBlank() ? subjectCpid : patientId;
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("patient_id is required");
        }
        Page<ImmunizationEntity> result = immunizationService.list(subject, page, size);
        List<Map<String, Object>> items = result.getContent().stream()
                .map(ImmunizationController::toMap).collect(Collectors.toList());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("page", result.getNumber());
        out.put("size", result.getSize());
        out.put("total", result.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> record(@RequestBody Map<String, Object> body) {
        // Authz: RBAC at ext_authz; subject relationship bound in ImmunizationService.
        ImmunizationEntity row = immunizationService.record(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toMap(row), correlationId()));
    }

    /** Confirms a dose reported from elsewhere, after which it counts towards the series. */
    @PostMapping("/{immunizationId}/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verify(
            @PathVariable UUID immunizationId,
            @RequestBody(required = false) Map<String, Object> body) {
        ImmunizationEntity row = immunizationService.verify(
                immunizationId, body == null ? Map.of() : body);
        return ResponseEntity.ok(ApiResponse.ok(toMap(row), correlationId()));
    }

    private static Map<String, Object> toMap(ImmunizationEntity row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("immunization_id", row.getImmunizationId().toString());
        m.put("patient_id", row.getSubjectCpid());
        m.put("subject_cpid", row.getSubjectCpid());
        m.put("journey_id", row.getJourneyId());
        m.put("encounter_id", row.getEncounterId());

        m.put("vaccine_name", row.getVaccineName());
        m.put("vaccine_code", row.getVaccineCode());
        m.put("code_system", row.getCodeSystem());
        m.put("dose_number", row.getDoseNumber());
        m.put("series", row.getSeries());
        // Legacy alias retained for the existing BFF/UI contract.
        m.put("dose_sequence", row.getSeries());
        m.put("occurrence_at", row.getOccurrenceAt() != null ? row.getOccurrenceAt().toString() : null);

        m.put("lot_number", row.getLotNumber());
        m.put("expiry_date", row.getExpiryDate() != null ? row.getExpiryDate().toString() : null);
        m.put("expiration_date", row.getExpiryDate() != null ? row.getExpiryDate().toString() : null);
        m.put("diluent_lot_number", row.getDiluentLotNumber());

        m.put("dose_quantity", row.getDoseQuantity());
        m.put("route", row.getRoute());
        m.put("body_site", row.getBodySite());
        m.put("site", row.getBodySite());
        m.put("administered_by", row.getAdministeredBy());
        m.put("delivery_context", row.getDeliveryContext());
        m.put("campaign_ref", row.getCampaignRef());
        m.put("outreach_location", row.getOutreachLocation());

        m.put("status", row.getStatus());
        m.put("status_reason", row.getStatusReason());
        m.put("deferred_until", row.getDeferredUntil() != null ? row.getDeferredUntil().toString() : null);
        m.put("counts_towards_series", row.countsTowardsSeries());
        m.put("source_reference", row.getSourceReference());
        m.put("verified_by", row.getVerifiedBy());
        m.put("verified_at", row.getVerifiedAt() != null ? row.getVerifiedAt().toString() : null);
        m.put("aefi_case_reference", row.getAefiCaseReference());

        m.put("notes", row.getNotes());
        m.put("recorded_by", row.getRecordedBy());
        m.put("created_at", row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
        return m;
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }
}
