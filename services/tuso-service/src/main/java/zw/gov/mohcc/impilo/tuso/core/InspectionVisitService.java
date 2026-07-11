package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityInspectionEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionFindingEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionFindingStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionResponseEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionVisitEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RectificationStatus;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityInspectionRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ComplianceActionEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ComplianceActionRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.InspectionFindingRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.InspectionResponseRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.InspectionVisitRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Inspection visits and structured per-item responses (HPA-2017-V1 §10-13).
 *
 * <p>The existing {@code facility_inspection} row is the inspection CASE; a
 * visit is one physical/desktop execution with a typed team (lead inspector,
 * council nominees, conflict-of-interest declarations). Every checklist item
 * response retains template identity + version, evidence, inspector and
 * timestamps. NON_COMPLIANT responses derive findings with rule-configured
 * remediation windows — never hard-coded 2017 periods.</p>
 */
@Service
public class InspectionVisitService {

    private static final Logger log = LoggerFactory.getLogger(InspectionVisitService.class);

    private static final Set<String> VALID_RESPONSES = Set.of(
            "COMPLIANT", "NON_COMPLIANT", "NOT_APPLICABLE", "NOT_OBSERVED", "UNABLE_TO_VERIFY");
    private static final Set<String> INSPECTOR_ROLES = Set.of(
            "HPA_INSPECTOR", "HPA_REGISTRAR", "HPA_ADMIN", "SYSTEM_ADMIN", "DEVELOPER");

    private final InspectionVisitRepository visitRepository;
    private final InspectionResponseRepository responseRepository;
    private final FacilityInspectionRepository inspectionRepository;
    private final InspectionFindingRepository findingRepository;
    private final RegulatoryRuleService ruleService;
    private final EventOutboxRepository outboxRepository;
    private final ComplianceActionRepository complianceActionRepository;
    private final InspectionContentService inspectionContentService;

    public InspectionVisitService(InspectionVisitRepository visitRepository,
                                  InspectionResponseRepository responseRepository,
                                  FacilityInspectionRepository inspectionRepository,
                                  InspectionFindingRepository findingRepository,
                                  RegulatoryRuleService ruleService,
                                  EventOutboxRepository outboxRepository,
                                  ComplianceActionRepository complianceActionRepository,
                                  InspectionContentService inspectionContentService) {
        this.visitRepository = visitRepository;
        this.responseRepository = responseRepository;
        this.inspectionRepository = inspectionRepository;
        this.findingRepository = findingRepository;
        this.ruleService = ruleService;
        this.outboxRepository = outboxRepository;
        this.complianceActionRepository = complianceActionRepository;
        this.inspectionContentService = inspectionContentService;
    }

    public record CreateVisitRequest(UUID inspectionId, LocalDate scheduledDate, String mode,
                                     String leadInspectorId, String leadInspectorName,
                                     List<Map<String, Object>> team, String notes) {}

    public record ItemResponse(String itemCode, String itemSection, String requirementText,
                               String response, Map<String, Object> measurement,
                               String comments, List<Map<String, Object>> evidenceRefs,
                               String severity, Boolean critical) {}

    @Transactional
    public InspectionVisitEntity createVisit(CreateVisitRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertInspector(ctx, "schedule an inspection visit");
        FacilityInspectionEntity inspection = requireInspection(request.inspectionId(), ctx);

        int nextNumber = visitRepository.findFirstByInspectionIdOrderByVisitNumberDesc(request.inspectionId())
                .map(v -> v.getVisitNumber() + 1).orElse(1);

        InspectionVisitEntity visit = new InspectionVisitEntity();
        visit.setTenantId(ctx.tenantId());
        visit.setInspectionId(request.inspectionId());
        visit.setVisitNumber(nextNumber);
        visit.setScheduledDate(request.scheduledDate());
        visit.setMode(request.mode() != null ? request.mode() : "ONSITE");
        visit.setLeadInspectorId(request.leadInspectorId() != null ? request.leadInspectorId() : ctx.actorId());
        visit.setLeadInspectorName(request.leadInspectorName());
        visit.setTeam(request.team() != null ? request.team() : List.of());
        visit.setStatus("PLANNED");
        visit.setNotes(request.notes());
        visit.setCreatedBy(ctx.actorId());
        visit.setUpdatedBy(ctx.actorId());
        visit = visitRepository.save(visit);
        log.info("Inspection visit {} (#{}) created for case {}", visit.getVisitId(), nextNumber, request.inspectionId());
        return visit;
    }

    /** Record (upsert) structured item responses for a visit. */
    @Transactional
    public List<InspectionResponseEntity> recordResponses(UUID visitId, List<ItemResponse> items) {
        TrustContext ctx = TrustContextHolder.require();
        assertInspector(ctx, "record inspection responses");
        InspectionVisitEntity visit = requireVisit(visitId, ctx);
        FacilityInspectionEntity inspection = requireInspection(visit.getInspectionId(), ctx);

        if ("COMPLETED".equals(visit.getStatus())) {
            throw new IllegalStateException("Visit is completed — responses are immutable; open a follow-up visit");
        }
        if (!"IN_PROGRESS".equals(visit.getStatus())) {
            visit.setStatus("IN_PROGRESS");
            if (visit.getActualDate() == null) {
                visit.setActualDate(LocalDate.now());
            }
        }
        visit.setUpdatedBy(ctx.actorId());
        visitRepository.save(visit);

        List<InspectionResponseEntity> saved = new ArrayList<>();
        for (ItemResponse item : items) {
            if (item.itemCode() == null || item.itemCode().isBlank()) {
                throw new IllegalArgumentException("Every response requires an itemCode (template traceability)");
            }
            String verdict = item.response() == null ? "" : item.response().toUpperCase();
            if (!VALID_RESPONSES.contains(verdict)) {
                throw new IllegalArgumentException("Response for " + item.itemCode()
                        + " must be one of " + VALID_RESPONSES);
            }
            InspectionResponseEntity response = responseRepository
                    .findByVisitIdAndItemCode(visitId, item.itemCode())
                    .orElseGet(InspectionResponseEntity::new);
            response.setTenantId(ctx.tenantId());
            response.setVisitId(visitId);
            response.setTemplateId(inspection.getTemplate() != null ? inspection.getTemplate().getTemplateId() : null);
            response.setTemplateVersion(inspection.getTemplate() != null ? inspection.getTemplate().getVersion() : null);
            response.setItemCode(item.itemCode());
            response.setItemSection(item.itemSection());
            response.setRequirementText(item.requirementText());
            response.setResponse(verdict);
            response.setMeasurement(item.measurement());
            response.setComments(item.comments());
            response.setEvidenceRefs(item.evidenceRefs());
            response.setInspectorId(ctx.actorId());
            response.setUpdatedBy(ctx.actorId());
            saved.add(responseRepository.save(response));

            // NON_COMPLIANT derives a finding with a rule-configured window.
            if ("NON_COMPLIANT".equals(verdict)) {
                boolean critical = Boolean.TRUE.equals(item.critical());
                String severity = item.severity() != null ? item.severity().toUpperCase() : (critical ? "CRITICAL" : "MAJOR");
                InspectionFindingEntity finding = new InspectionFindingEntity();
                finding.setInspection(inspection);
                finding.setResponseId(response.getResponseId());
                finding.setChecklistItemCode(item.itemCode());
                finding.setChecklistSection(item.itemSection());
                finding.setRequirementText(item.requirementText() != null ? item.requirementText()
                        : "Requirement " + item.itemCode());
                finding.setCriticalFlag(critical);
                finding.setStatus(InspectionFindingStatus.FAIL);
                finding.setSeverity(severity);
                finding.setComments(item.comments());
                finding.setEvidenceRefs(item.evidenceRefs());
                finding.setRectificationDeadline(LocalDate.now()
                        .plusDays(ruleService.remediationWindowDays(severity, critical)));
                finding.setRectificationStatus(RectificationStatus.OPEN);
                finding = findingRepository.save(finding);

                // Derived failures open a corrective action (CAPA), mirroring
                // the recordInspectionOutcome path.
                ComplianceActionEntity action = new ComplianceActionEntity();
                action.setTenantId(ctx.tenantId());
                action.setFacility(inspection.getFacility());
                action.setInspectionFinding(finding);
                action.setActionType("RECTIFY_SHORTFALL");
                action.setDueDate(finding.getRectificationDeadline());
                action.setCreatedBy(ctx.actorId());
                action.setUpdatedBy(ctx.actorId());
                complianceActionRepository.save(action);
            }
        }
        return saved;
    }

    /**
     * Save-and-resume progress for a visit: which checklist items are answered,
     * which mandatory items are outstanding, and which recorded responses still
     * lack the evidence the manual expects.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> progress(UUID visitId) {
        TrustContext ctx = TrustContextHolder.require();
        InspectionVisitEntity visit = requireVisit(visitId, ctx);
        FacilityInspectionEntity inspection = requireInspection(visit.getInspectionId(), ctx);
        Map<String, Object> checklist = inspectionContentService.checklistFor(inspection);

        Map<String, InspectionResponseEntity> responsesByCode = new LinkedHashMap<>();
        for (InspectionResponseEntity response : responseRepository.findByVisitIdOrderByItemCodeAsc(visitId)) {
            responsesByCode.put(response.getItemCode(), response);
        }

        List<Map<String, Object>> outstandingMandatory = new ArrayList<>();
        List<Map<String, Object>> missingEvidence = new ArrayList<>();
        int totalItems = 0;
        List<Map<String, Object>> allItems = new ArrayList<>();
        if (checklist.get("modules") instanceof List<?> modules) {
            for (Object moduleObj : modules) {
                if (moduleObj instanceof Map<?, ?> module && module.get("items") instanceof List<?> items) {
                    for (Object itemObj : items) {
                        if (itemObj instanceof Map<?, ?> item) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> typed = (Map<String, Object>) item;
                            allItems.add(typed);
                        }
                    }
                }
            }
        } else if (checklist.get("items") instanceof List<?> items) {
            for (Object itemObj : items) {
                if (itemObj instanceof Map<?, ?> item) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) item;
                    allItems.add(typed);
                }
            }
        }
        for (Map<String, Object> item : allItems) {
            totalItems++;
            String code = String.valueOf(item.get("code"));
            InspectionResponseEntity response = responsesByCode.get(code);
            if (response == null) {
                if ("MANDATORY".equals(item.get("obligation"))) {
                    outstandingMandatory.add(Map.of(
                            "code", code,
                            "section", String.valueOf(item.getOrDefault("section", "")),
                            "requirement", String.valueOf(item.getOrDefault("requirement", ""))));
                }
                continue;
            }
            Object expectedEvidence = item.get("evidence");
            boolean needsEvidence = expectedEvidence != null
                    && !"OBSERVATION".equals(expectedEvidence) && !"INTERVIEW".equals(expectedEvidence);
            boolean hasEvidence = response.getEvidenceRefs() != null && !response.getEvidenceRefs().isEmpty();
            boolean nonApplicable = "NOT_APPLICABLE".equals(response.getResponse())
                    || "NOT_OBSERVED".equals(response.getResponse());
            if (needsEvidence && !hasEvidence && !nonApplicable) {
                missingEvidence.add(Map.of(
                        "code", code,
                        "expectedEvidence", String.valueOf(expectedEvidence),
                        "response", response.getResponse()));
            }
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("visitId", visitId);
        view.put("visitStatus", visit.getStatus());
        view.put("totalItems", totalItems);
        view.put("answered", responsesByCode.size());
        view.put("outstandingMandatory", outstandingMandatory);
        view.put("missingRequiredEvidence", missingEvidence);
        return view;
    }

    /** Complete a visit — responses freeze; summary event emitted. */
    @Transactional
    public InspectionVisitEntity completeVisit(UUID visitId, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        assertInspector(ctx, "complete an inspection visit");
        InspectionVisitEntity visit = requireVisit(visitId, ctx);
        if ("COMPLETED".equals(visit.getStatus())) {
            return visit;
        }
        long total = responseRepository.countByVisitId(visitId);
        if (total == 0) {
            throw new IllegalStateException("A visit cannot be completed without any recorded responses");
        }
        long nonCompliant = responseRepository.countByVisitIdAndResponse(visitId, "NON_COMPLIANT");
        visit.setStatus("COMPLETED");
        if (visit.getActualDate() == null) {
            visit.setActualDate(LocalDate.now());
        }
        if (notes != null && !notes.isBlank()) {
            visit.setNotes(visit.getNotes() == null ? notes : visit.getNotes() + "\n" + notes);
        }
        visit.setUpdatedBy(ctx.actorId());
        visit = visitRepository.save(visit);

        FacilityInspectionEntity inspection = requireInspection(visit.getInspectionId(), ctx);
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("FACILITY");
        event.setAggregateId(String.valueOf(inspection.getFacility().getId()));
        event.setEventType("tuso.facility.inspection.visit_recorded");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inspectionId", visit.getInspectionId().toString());
        payload.put("visitId", visitId.toString());
        payload.put("visitNumber", visit.getVisitNumber());
        payload.put("responses", total);
        payload.put("nonCompliant", nonCompliant);
        event.setPayload(payload);
        event.setTenantId(ctx.tenantId().toString());
        event.setProducer("tuso");
        event.setSubjectType("Facility");
        event.setSubjectId(String.valueOf(inspection.getFacility().getId()));
        event.setPartitionKey(String.valueOf(inspection.getFacility().getId()));
        event.setSchemaVersion(1);
        event.setPodId("national-spine");
        event.setIdempotencyKey("tuso:visit:" + visitId + ":" + UUID.randomUUID());
        outboxRepository.save(event);
        log.info("Visit {} completed: {} responses, {} non-compliant", visitId, total, nonCompliant);
        return visit;
    }

    @Transactional(readOnly = true)
    public List<InspectionVisitEntity> listVisits(UUID inspectionId) {
        return visitRepository.findByInspectionIdOrderByVisitNumberAsc(inspectionId);
    }

    @Transactional(readOnly = true)
    public List<InspectionResponseEntity> listResponses(UUID visitId) {
        return responseRepository.findByVisitIdOrderByItemCodeAsc(visitId);
    }

    // ---- helpers ---------------------------------------------------------

    private InspectionVisitEntity requireVisit(UUID visitId, TrustContext ctx) {
        InspectionVisitEntity visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new IllegalArgumentException("Visit not found: " + visitId));
        if (!visit.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation");
        }
        return visit;
    }

    private FacilityInspectionEntity requireInspection(UUID inspectionId, TrustContext ctx) {
        FacilityInspectionEntity inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new IllegalArgumentException("Inspection not found: " + inspectionId));
        if (!inspection.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation");
        }
        return inspection;
    }

    private static void assertInspector(TrustContext ctx, String action) {
        String actorType = ctx.actorType();
        if (actorType == null || actorType.isBlank()) {
            return;
        }
        if (!INSPECTOR_ROLES.contains(actorType)) {
            throw new SecurityException("Actor type " + actorType + " cannot " + action);
        }
    }
}
