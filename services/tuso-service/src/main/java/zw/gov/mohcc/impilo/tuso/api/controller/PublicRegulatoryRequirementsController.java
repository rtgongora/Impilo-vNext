package zw.gov.mohcc.impilo.tuso.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.core.ApplicationGovernanceService;
import zw.gov.mohcc.impilo.tuso.core.RegulatoryFeeScheduleService;
import zw.gov.mohcc.impilo.tuso.core.RegulatoryRuleService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityClassificationEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryApplicationTypeEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryFeeScheduleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryRuleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityClassificationRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public-facing REST API for the facility-registry REGULATORY REFERENCE catalogue.
 *
 * This is a deliberately allow-listed, no-PII, no-auth public lane. It exposes only
 * TENANT-AGNOSTIC national catalogue data — regulatory application types, facility
 * classifications, published regulatory rules, and fee-schedule structure — so that
 * prospective applicants and the public can understand facility-registration
 * requirements before authenticating.
 *
 * Because the underlying catalogue tables carry NO {@code tenant_id}, this controller
 * intentionally does NOT touch {@code TrustContextHolder}: there is no tenant scope to
 * apply. Each response item is rebuilt as a plain allow-listed {@link Map}; internal
 * identifiers, source/provenance columns, validation status, audit fields, ownership,
 * approver, implementation hooks, and raw value JSON are never emitted. Each of the four
 * sources is read under its own guard so a single failing source degrades to an empty
 * list rather than failing the whole response.
 */
@RestController
@RequestMapping("/v1/public/facility-registry")
public class PublicRegulatoryRequirementsController {

    private static final Logger log = LoggerFactory.getLogger(PublicRegulatoryRequirementsController.class);

    private static final String DEFAULT_SCHEDULE_CODE = "SI_78_2017";

    private final ApplicationGovernanceService applicationGovernanceService;
    private final FacilityClassificationRepository facilityClassificationRepository;
    private final RegulatoryRuleService regulatoryRuleService;
    private final RegulatoryFeeScheduleService feeScheduleService;

    public PublicRegulatoryRequirementsController(
            ApplicationGovernanceService applicationGovernanceService,
            FacilityClassificationRepository facilityClassificationRepository,
            RegulatoryRuleService regulatoryRuleService,
            RegulatoryFeeScheduleService feeScheduleService) {
        this.applicationGovernanceService = applicationGovernanceService;
        this.facilityClassificationRepository = facilityClassificationRepository;
        this.regulatoryRuleService = regulatoryRuleService;
        this.feeScheduleService = feeScheduleService;
    }

    @GetMapping("")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRegulatoryReference(
            @RequestParam(required = false) String scheduleCode,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        log.info("Public regulatory-reference catalogue requested [scheduleCode={}] correlationId={}",
                scheduleCode, correlationId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applicationTypes", applicationTypes());
        body.put("classifications", classifications());
        body.put("rules", rules());
        body.put("feeSchedules", feeSchedules(scheduleCode));

        return ResponseEntity.ok(ApiResponse.ok(body, correlationId));
    }

    // ---- Allow-listed source mappers (each guarded independently) ----

    private List<Map<String, Object>> applicationTypes() {
        try {
            List<RegulatoryApplicationTypeEntity> rows = applicationGovernanceService.listApplicationTypes();
            return rows.stream().map(t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", t.getCode());
                m.put("displayName", t.getDisplayName());
                m.put("route", t.getRoute());
                m.put("councilReviewRequired", t.isCouncilReviewRequired());
                m.put("inspectionRequired", t.isInspectionRequired());
                m.put("feeRequired", t.isFeeRequired());
                m.put("evidenceRequirements", t.getEvidenceRequirements());
                m.put("manualRouteSummary", t.getManualRouteSummary());
                return m;
            }).toList();
        } catch (RuntimeException e) {
            log.warn("Public regulatory-reference: applicationTypes source failed, returning empty list", e);
            return List.of();
        }
    }

    private List<Map<String, Object>> classifications() {
        try {
            List<FacilityClassificationEntity> rows =
                    facilityClassificationRepository.findByActiveTrueOrderByClassCodeAscFacilityTypeLabelAsc();
            return rows.stream().map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("classCode", c.getClassCode());
                m.put("facilityTypeLabel", c.getFacilityTypeLabel());
                return m;
            }).toList();
        } catch (RuntimeException e) {
            log.warn("Public regulatory-reference: classifications source failed, returning empty list", e);
            return List.of();
        }
    }

    private List<Map<String, Object>> rules() {
        try {
            List<RegulatoryRuleEntity> rows = regulatoryRuleService.listRules();
            return rows.stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", r.getCode());
                m.put("ruleKind", r.getRuleKind());
                m.put("statement", r.getStatement());
                m.put("status", r.getStatus());
                m.put("effectiveFrom", toStringOrNull(r.getEffectiveFrom()));
                m.put("effectiveTo", toStringOrNull(r.getEffectiveTo()));
                return m;
            }).toList();
        } catch (RuntimeException e) {
            log.warn("Public regulatory-reference: rules source failed, returning empty list", e);
            return List.of();
        }
    }

    private List<Map<String, Object>> feeSchedules(String scheduleCode) {
        try {
            String code = (scheduleCode == null || scheduleCode.isBlank()) ? DEFAULT_SCHEDULE_CODE : scheduleCode;
            List<RegulatoryFeeScheduleEntity> rows = feeScheduleService.listBySchedule(code);
            return rows.stream().map(f -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("scheduleCode", f.getScheduleCode());
                m.put("feeCode", f.getFeeCode());
                m.put("appliesToClassCode", f.getAppliesToClassCode());
                m.put("amount", f.getAmount());
                m.put("currency", f.getCurrency());
                m.put("sourceInstrument", f.getSourceInstrument());
                m.put("status", f.getStatus());
                m.put("effectiveFrom", toStringOrNull(f.getEffectiveFrom()));
                return m;
            }).toList();
        } catch (RuntimeException e) {
            log.warn("Public regulatory-reference: feeSchedules source failed, returning empty list", e);
            return List.of();
        }
    }

    private static String toStringOrNull(LocalDate date) {
        return date == null ? null : date.toString();
    }
}
