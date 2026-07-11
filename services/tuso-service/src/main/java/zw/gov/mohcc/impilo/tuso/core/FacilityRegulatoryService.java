package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityRegulatoryDtos;
import zw.gov.mohcc.impilo.tuso.persistence.entity.*;
import zw.gov.mohcc.impilo.tuso.persistence.repository.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FacilityRegulatoryService {

    private static final Logger log = LoggerFactory.getLogger(FacilityRegulatoryService.class);

    // HPA_REGISTRAR is the authority's registry officer: raises/administers
    // applications on behalf of applicants, schedules inspections and records
    // committee resolutions. HPA_INSPECTOR carries field verdicts only.
    private static final Set<String> APPLICANT_ROLES = Set.of(
            "FACILITY_APPLICANT", "FACILITY_MANAGER", "FACILITY_ADMIN", "HPA_REGISTRAR", "SYSTEM_ADMIN", "DEVELOPER");
    private static final Set<String> INSPECTOR_ROLES = Set.of(
            "HPA_INSPECTOR", "HPA_REGISTRAR", "HPA_ADMIN", "SYSTEM_ADMIN", "DEVELOPER");
    private static final Set<String> COMMITTEE_ROLES = Set.of(
            "HPA_COMMITTEE_MEMBER", "HPA_REGISTRAR", "HPA_ADMIN", "COUNCIL_REVIEWER", "SYSTEM_ADMIN", "DEVELOPER");

    private final FacilityRepository facilityRepository;
    private final FacilityGeoRepository facilityGeoRepository;
    private final FacilityApplicationRepository applicationRepository;
    private final FacilityDocumentRepository documentRepository;
    private final InspectionChecklistTemplateRepository checklistTemplateRepository;
    private final FacilityInspectionRepository inspectionRepository;
    private final InspectionFindingRepository findingRepository;
    private final ComplianceActionRepository complianceActionRepository;
    private final CommitteeReviewRepository committeeReviewRepository;
    private final FacilityCertificateRepository certificateRepository;
    private final FacilityUnitRepository facilityUnitRepository;
    private final PractitionerInChargeAssignmentRepository practitionerRepository;
    private final EnforcementCaseRepository enforcementCaseRepository;
    private final FacilityStatusHistoryRepository statusHistoryRepository;
    private final FacilityAuditEventRepository auditEventRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public FacilityRegulatoryService(FacilityRepository facilityRepository,
                                     FacilityGeoRepository facilityGeoRepository,
                                     FacilityApplicationRepository applicationRepository,
                                     FacilityDocumentRepository documentRepository,
                                     InspectionChecklistTemplateRepository checklistTemplateRepository,
                                     FacilityInspectionRepository inspectionRepository,
                                     InspectionFindingRepository findingRepository,
                                     ComplianceActionRepository complianceActionRepository,
                                     CommitteeReviewRepository committeeReviewRepository,
                                     FacilityCertificateRepository certificateRepository,
                                     FacilityUnitRepository facilityUnitRepository,
                                     PractitionerInChargeAssignmentRepository practitionerRepository,
                                     EnforcementCaseRepository enforcementCaseRepository,
                                     FacilityStatusHistoryRepository statusHistoryRepository,
                                     FacilityAuditEventRepository auditEventRepository,
                                     EventOutboxRepository outboxRepository,
                                     ObjectMapper objectMapper) {
        this.facilityRepository = facilityRepository;
        this.facilityGeoRepository = facilityGeoRepository;
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.checklistTemplateRepository = checklistTemplateRepository;
        this.inspectionRepository = inspectionRepository;
        this.findingRepository = findingRepository;
        this.complianceActionRepository = complianceActionRepository;
        this.committeeReviewRepository = committeeReviewRepository;
        this.certificateRepository = certificateRepository;
        this.facilityUnitRepository = facilityUnitRepository;
        this.practitionerRepository = practitionerRepository;
        this.enforcementCaseRepository = enforcementCaseRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.auditEventRepository = auditEventRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FacilityRegulatoryDtos.FacilityRegulatoryProfileResponse createApplication(
            FacilityRegulatoryDtos.CreateFacilityApplicationRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertAllowed(ctx, APPLICANT_ROLES);

        FacilityEntity facility = request.facilityId() != null
                ? requireFacility(request.facilityId(), ctx.tenantId())
                : createApplicationFacility(request, ctx);

        FacilityApplicationEntity application = new FacilityApplicationEntity();
        application.setTenantId(ctx.tenantId());
        application.setFacility(facility);
        application.setApplicationType(request.applicationType());
        application.setPathway(firstNonBlank(request.pathway(), request.registrationPathway(), facility.getRegistrationPathway()));
        application.setApplicantName(request.applicantName());
        application.setApplicantEmail(request.applicantEmail());
        application.setApplicantPhone(request.applicantPhone());
        application.setApplicantOrganisation(request.applicantOrganisation());
        application.setPriority(firstNonBlank(request.priority(), "NORMAL"));
        application.setFeeState(firstNonBlank(request.feeState(), "PENDING"));
        application.setWorkflowNotes(request.workflowNotes());
        application.setInstitutionFileNumber(firstNonBlank(facility.getInstitutionFileNumber(), generateInstitutionFileNumber(facility)));
        application.setRequestedChanges(buildRequestedChanges(request));
        application.setMetadata(defaultMap(request.metadata()));
        application.setCreatedBy(ctx.actorId());
        application.setUpdatedBy(ctx.actorId());
        application = applicationRepository.save(application);

        if (facility.getInstitutionFileNumber() == null) {
            facility.setInstitutionFileNumber(application.getInstitutionFileNumber());
        }

        FacilityRegulatoryStatus status = request.applicationType() == FacilityApplicationType.RENEWAL
                ? FacilityRegulatoryStatus.RENEWAL_IN_PROGRESS
                : FacilityRegulatoryStatus.APPLICATION_IN_PROGRESS;
        applyFacilityStatus(facility, status, FacilityApplicationState.DRAFT, "Application created",
                application.getApplicationId(), ctx, "FACILITY_APPLICANT");

        audit(facility, application, "APPLICATION_CREATED", "FACILITY_APPLICATION",
                application.getApplicationId().toString(), null,
                Map.of("applicationType", request.applicationType().name(), "pathway", nullSafe(application.getPathway())), ctx);
        publishEvent(facility, "tuso.facility.application.created", Map.of(
                "facilityId", facility.getId(),
                "applicationId", application.getApplicationId(),
                "applicationType", request.applicationType().name(),
                "regulatoryStatus", facility.getRegulatoryStatus().name(),
                "workflowState", application.getCurrentWorkflowState().name()
        ), ctx);

        return getFacilityProfile(facility.getId());
    }

    @Transactional
    public FacilityRegulatoryDtos.FacilityRegulatoryProfileResponse submitApplication(UUID applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        assertAllowed(ctx, APPLICANT_ROLES);

        FacilityApplicationEntity application = requireApplication(applicationId, ctx.tenantId());
        validateApplicationSubmission(application);
        application.setSubmissionDate(Instant.now());
        application.setCurrentWorkflowState(FacilityApplicationState.SUBMITTED);
        application.setUpdatedBy(ctx.actorId());
        applicationRepository.save(application);

        applyFacilityStatus(application.getFacility(), FacilityRegulatoryStatus.UNDER_INITIAL_REVIEW,
                FacilityApplicationState.SUBMITTED, "Application submitted", applicationId, ctx, "FACILITY_APPLICANT");

        audit(application.getFacility(), application, "APPLICATION_SUBMITTED", "FACILITY_APPLICATION",
                applicationId.toString(), null,
                Map.of("workflowState", application.getCurrentWorkflowState().name()), ctx);
        publishEvent(application.getFacility(), "tuso.facility.application.submitted", Map.of(
                "facilityId", application.getFacility().getId(),
                "applicationId", applicationId,
                "workflowState", application.getCurrentWorkflowState().name()
        ), ctx);
        return getFacilityProfile(application.getFacility().getId());
    }

    @Transactional
    public FacilityRegulatoryDtos.FacilityRegulatoryProfileResponse markReadyForInspection(UUID applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        assertAllowed(ctx, INSPECTOR_ROLES);

        FacilityApplicationEntity application = requireApplication(applicationId, ctx.tenantId());
        application.setCurrentWorkflowState(FacilityApplicationState.READY_FOR_INSPECTION);
        application.setUpdatedBy(ctx.actorId());
        applicationRepository.save(application);
        applyFacilityStatus(application.getFacility(), FacilityRegulatoryStatus.PENDING_INSPECTION,
                FacilityApplicationState.READY_FOR_INSPECTION, "Application cleared for inspection", applicationId, ctx, "HPA_ADMIN");
        audit(application.getFacility(), application, "APPLICATION_READY_FOR_INSPECTION", "FACILITY_APPLICATION",
                applicationId.toString(), null,
                Map.of("workflowState", application.getCurrentWorkflowState().name()), ctx);
        return getFacilityProfile(application.getFacility().getId());
    }

    @Transactional
    public FacilityRegulatoryDtos.DocumentView uploadDocument(FacilityRegulatoryDtos.UploadFacilityDocumentRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertAllowed(ctx, APPLICANT_ROLES);

        FacilityEntity facility = request.facilityId() != null ? requireFacility(request.facilityId(), ctx.tenantId()) : null;
        FacilityApplicationEntity application = request.applicationId() != null ? requireApplication(request.applicationId(), ctx.tenantId()) : null;
        if (facility == null && application != null) {
            facility = application.getFacility();
        }
        if (facility == null) {
            throw new IllegalArgumentException("Facility or application is required for document upload");
        }

        FacilityDocumentEntity document = new FacilityDocumentEntity();
        document.setTenantId(ctx.tenantId());
        document.setFacility(facility);
        document.setApplication(application);
        document.setDocumentType(request.documentType());
        document.setFileReference(request.fileReference());
        document.setVersion(request.version() != null ? request.version() : 1);
        document.setVerificationState(firstNonBlank(request.verificationState(), "UNVERIFIED"));
        document.setUploadedBy(ctx.actorId());
        document.setNotes(request.notes());
        document.setMetadata(defaultMap(request.metadata()));
        document = documentRepository.save(document);

        audit(facility, application, "DOCUMENT_UPLOADED", "FACILITY_DOCUMENT", document.getDocumentId().toString(),
                null, Map.of("documentType", request.documentType(), "verificationState", document.getVerificationState()), ctx);
        publishEvent(facility, "tuso.facility.document.uploaded", Map.of(
                "facilityId", facility.getId(),
                "applicationId", application != null ? application.getApplicationId() : null,
                "documentId", document.getDocumentId(),
                "documentType", document.getDocumentType()
        ), ctx);
        return toDocumentView(document);
    }

    @Transactional(readOnly = true)
    public Page<FacilityRegulatoryDtos.FacilityRegistrySummaryResponse> searchFacilities(
            String query, FacilityRegulatoryStatus regulatoryStatus, String facilityType,
            String province, String district, int page, int size) {
        TrustContext ctx = TrustContextHolder.require();
        Page<FacilityEntity> facilities = (query != null && !query.isBlank())
                ? facilityRepository.searchByNameAndFilters(ctx.tenantId(), query.trim(), facilityType, null,
                    regulatoryStatus, district, province, PageRequest.of(page, size))
                : facilityRepository.findByFilters(ctx.tenantId(), facilityType, null, regulatoryStatus,
                    district, province, PageRequest.of(page, size));
        return facilities.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public FacilityRegulatoryDtos.FacilityRegulatoryProfileResponse getFacilityProfile(Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityId, ctx.tenantId());
        FacilityGeoEntity geo = facilityGeoRepository.findByFacilityId(facilityId).orElse(null);
        List<FacilityApplicationEntity> applications = applicationRepository.findByFacilityIdOrderByCreatedAtDesc(facilityId);
        List<FacilityDocumentEntity> documents = documentRepository.findByFacilityIdOrderByUploadedAtDesc(facilityId);
        List<FacilityInspectionEntity> inspections = inspectionRepository.findByFacilityIdOrderByCreatedAtDesc(facilityId);
        List<InspectionFindingEntity> findings = inspections.stream()
                .flatMap(inspection -> findingRepository.findByInspectionInspectionIdOrderByCreatedAtAsc(inspection.getInspectionId()).stream())
                .toList();
        List<ComplianceActionEntity> complianceActions = complianceActionRepository.findByFacilityIdOrderByCreatedAtDesc(facilityId);
        List<CommitteeReviewEntity> reviews = committeeReviewRepository.findByFacilityIdOrderByDecidedAtDesc(facilityId);
        List<FacilityCertificateEntity> certificates = certificateRepository.findByFacilityIdOrderByIssueDateDesc(facilityId);
        List<FacilityUnitEntity> units = facilityUnitRepository.findByFacilityIdOrderByCreatedAtAsc(facilityId);
        List<PractitionerInChargeAssignmentEntity> practitioners = practitionerRepository.findByFacilityIdOrderByStartDateDesc(facilityId);
        List<FacilityStatusHistoryEntity> statusHistory = statusHistoryRepository.findByFacilityIdOrderByChangedAtDesc(facilityId);
        List<FacilityAuditEventEntity> auditEvents = auditEventRepository.findByFacilityIdOrderByCreatedAtDesc(facilityId);
        List<EnforcementCaseEntity> enforcementCases = enforcementCaseRepository.findByFacilityIdOrderByOpenedAtDesc(facilityId);

        return new FacilityRegulatoryDtos.FacilityRegulatoryProfileResponse(
                toMaster(facility, geo),
                applications.stream().map(this::toApplicationView).toList(),
                documents.stream().map(this::toDocumentView).toList(),
                inspections.stream().map(this::toInspectionView).toList(),
                findings.stream().map(this::toFindingView).toList(),
                complianceActions.stream().map(this::toComplianceActionView).toList(),
                reviews.stream().map(this::toReviewView).toList(),
                certificates.stream().map(this::toCertificateView).toList(),
                units.stream().map(this::toUnitView).toList(),
                practitioners.stream().map(this::toPractitionerView).toList(),
                statusHistory.stream().map(this::toStatusHistoryView).toList(),
                auditEvents.stream().map(this::toAuditView).toList(),
                enforcementCases.stream().map(this::toEnforcementView).toList()
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listChecklistTemplates(FacilityInspectionType inspectionType, String facilityType) {
        TrustContextHolder.require();
        List<InspectionChecklistTemplateEntity> templates = checklistTemplateRepository.findApplicableTemplates(inspectionType, facilityType);
        return templates.stream()
                .map(template -> {
                    // Map.of rejects null values — several template fields are nullable.
                    Map<String, Object> view = new java.util.LinkedHashMap<>();
                    view.put("templateId", template.getTemplateId());
                    view.put("templateCode", template.getCode());
                    view.put("code", template.getCode());
                    view.put("name", template.getName());
                    view.put("version", template.getVersion());
                    view.put("facilityTypeApplicability", template.getFacilityTypeApplicability());
                    view.put("inspectionTypeApplicability",
                            template.getInspectionTypeApplicability() != null
                                    ? template.getInspectionTypeApplicability().name() : null);
                    view.put("description", template.getDescription());
                    view.put("items", template.getItemsJson());
                    return (Map<String, Object>) view;
                })
                .toList();
    }

    @Transactional
    public FacilityRegulatoryDtos.InspectionView scheduleInspection(
            FacilityRegulatoryDtos.ScheduleFacilityInspectionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertAllowed(ctx, INSPECTOR_ROLES);

        FacilityEntity facility = requireFacility(request.facilityId(), ctx.tenantId());
        FacilityApplicationEntity application = request.applicationId() != null ? requireApplication(request.applicationId(), ctx.tenantId()) : null;
        InspectionChecklistTemplateEntity template = resolveTemplate(request.templateCode(), request.inspectionType(), facility.getFacilityType());

        FacilityInspectionEntity inspection = new FacilityInspectionEntity();
        inspection.setTenantId(ctx.tenantId());
        inspection.setFacility(facility);
        inspection.setApplication(application);
        inspection.setInspectionType(request.inspectionType());
        inspection.setTemplate(template);
        inspection.setScheduledDate(request.scheduledDate());
        inspection.setInspectorAssignments(defaultList(request.inspectorAssignments()));
        inspection.setNotes(request.notes());
        inspection.setCaptureMode(firstNonBlank(request.captureMode(), "ONLINE"));
        inspection.setOfflineCaptureReference(request.offlineCaptureReference());
        inspection.setCreatedBy(ctx.actorId());
        inspection.setUpdatedBy(ctx.actorId());
        inspection = inspectionRepository.save(inspection);

        if (application != null) {
            application.setCurrentWorkflowState(FacilityApplicationState.INSPECTION_SCHEDULED);
            application.setUpdatedBy(ctx.actorId());
            applicationRepository.save(application);
            applyFacilityStatus(facility, FacilityRegulatoryStatus.PENDING_INSPECTION,
                    FacilityApplicationState.INSPECTION_SCHEDULED, "Inspection scheduled",
                    application.getApplicationId(), ctx, "HPA_INSPECTOR");
        }

        audit(facility, application, "INSPECTION_SCHEDULED", "FACILITY_INSPECTION", inspection.getInspectionId().toString(),
                null, Map.of("inspectionType", inspection.getInspectionType().name(), "scheduledDate", nullSafe(request.scheduledDate())), ctx);
        publishEvent(facility, "tuso.facility.inspection.scheduled", Map.of(
                "facilityId", facility.getId(),
                "applicationId", application != null ? application.getApplicationId() : null,
                "inspectionId", inspection.getInspectionId(),
                "inspectionType", inspection.getInspectionType().name(),
                "scheduledDate", inspection.getScheduledDate()
        ), ctx);
        return toInspectionView(inspection);
    }

    @Transactional
    public FacilityRegulatoryDtos.InspectionView recordInspectionOutcome(
            UUID inspectionId, FacilityRegulatoryDtos.RecordInspectionOutcomeRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertAllowed(ctx, INSPECTOR_ROLES);

        FacilityInspectionEntity inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new IllegalArgumentException("Inspection not found: " + inspectionId));
        if (!inspection.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation for inspection: " + inspectionId);
        }

        inspection.setActualDate(request.actualDate() != null ? request.actualDate() : LocalDate.now());
        inspection.setStatus(FacilityInspectionStatus.COMPLETED);
        inspection.setReportStatus("SUBMITTED");
        inspection.setNotes(request.notes());

        Map<String, Map<String, Object>> templateItems = indexTemplateItems(inspection.getTemplate());
        boolean hasCriticalFailure = false;
        boolean hasFailures = false;
        List<InspectionFindingEntity> savedFindings = new ArrayList<>();

        for (FacilityRegulatoryDtos.FindingInput input : request.findings() != null ? request.findings() : List.<FacilityRegulatoryDtos.FindingInput>of()) {
            Map<String, Object> templateItem = templateItems.get(input.checklistItemCode());
            if (inspection.getTemplate() != null && templateItem == null) {
                throw new IllegalArgumentException("Checklist item not found in template: " + input.checklistItemCode());
            }

            InspectionFindingEntity finding = new InspectionFindingEntity();
            finding.setInspection(inspection);
            finding.setChecklistItemCode(input.checklistItemCode());
            finding.setChecklistSection(firstNonBlank(input.checklistSection(), stringValue(templateItem, "section")));
            finding.setRequirementText(firstNonBlank(input.requirementText(), stringValue(templateItem, "requirement")));
            finding.setCriticalFlag(Boolean.TRUE.equals(input.criticalFlag()) || booleanValue(templateItem, "critical"));
            finding.setStatus(input.status());
            finding.setSeverity(firstNonBlank(input.severity(), stringValue(templateItem, "severity"), "MODERATE"));
            finding.setComments(input.comments());
            finding.setEvidenceRefs(defaultList(input.evidenceRefs()));

            if (input.status() == InspectionFindingStatus.FAIL) {
                hasFailures = true;
                if (finding.isCriticalFlag()) {
                    hasCriticalFailure = true;
                }
                finding.setRectificationStatus(RectificationStatus.OPEN);
                finding.setRectificationDeadline(input.rectificationDeadline() != null
                        ? input.rectificationDeadline() : LocalDate.now().plusDays(finding.isCriticalFlag() ? 14 : 30));
            } else {
                finding.setRectificationStatus(RectificationStatus.NOT_REQUIRED);
            }
            finding = findingRepository.save(finding);
            savedFindings.add(finding);

            if (input.status() == InspectionFindingStatus.FAIL) {
                ComplianceActionEntity action = new ComplianceActionEntity();
                action.setTenantId(ctx.tenantId());
                action.setFacility(inspection.getFacility());
                action.setInspectionFinding(finding);
                action.setActionType(firstNonBlank(input.actionType(), "RECTIFY_SHORTFALL"));
                action.setOwnerName(input.ownerName());
                action.setOwnerRole(input.ownerRole());
                action.setDueDate(finding.getRectificationDeadline());
                action.setCreatedBy(ctx.actorId());
                action.setUpdatedBy(ctx.actorId());
                complianceActionRepository.save(action);
            }
        }

        inspection.setOutcome(hasCriticalFailure ? "NON_COMPLIANT_CRITICAL" : hasFailures ? "NON_COMPLIANT" : "COMPLIANT");
        inspection.setRecommendation(firstNonBlank(request.recommendation(),
                hasCriticalFailure ? "ESCALATE_ENFORCEMENT_REVIEW" : hasFailures ? "RECTIFY_SHORTFALLS" : "COMMITTEE_REVIEW"));
        inspection.setSeveritySummary(hasCriticalFailure ? "CRITICAL" : hasFailures ? "MAJOR" : "COMPLIANT");
        inspection.setNextAction(firstNonBlank(request.nextAction(), hasFailures ? "RECTIFICATION_TRACKING" : "COMMITTEE_REVIEW"));
        inspection.setNextInspectionDueDate(request.nextInspectionDueDate());
        inspection.setEvidenceSummary(Map.of(
                "findingCount", savedFindings.size(),
                "failureCount", savedFindings.stream().filter(f -> f.getStatus() == InspectionFindingStatus.FAIL).count()
        ));
        inspection.setUpdatedBy(ctx.actorId());
        inspectionRepository.save(inspection);

        FacilityApplicationEntity application = inspection.getApplication();
        if (application != null) {
            if (hasFailures) {
                application.setCurrentWorkflowState(FacilityApplicationState.AWAITING_RECTIFICATION);
                application.setUpdatedBy(ctx.actorId());
                applicationRepository.save(application);
                applyFacilityStatus(inspection.getFacility(), FacilityRegulatoryStatus.PENDING_RECTIFICATION,
                        FacilityApplicationState.AWAITING_RECTIFICATION, "Inspection recorded with rectifications required",
                        application.getApplicationId(), ctx, "HPA_INSPECTOR");
            } else {
                application.setCurrentWorkflowState(FacilityApplicationState.READY_FOR_COMMITTEE);
                application.setUpdatedBy(ctx.actorId());
                applicationRepository.save(application);
                applyFacilityStatus(inspection.getFacility(), FacilityRegulatoryStatus.PENDING_COMMITTEE_REVIEW,
                        FacilityApplicationState.READY_FOR_COMMITTEE, "Inspection completed and compliant",
                        application.getApplicationId(), ctx, "HPA_INSPECTOR");
            }
        } else if (hasCriticalFailure) {
            applyFacilityStatus(inspection.getFacility(), FacilityRegulatoryStatus.RESTRICTED,
                    null, "Inspection identified critical compliance risk", null, ctx, "HPA_INSPECTOR");
        }

        audit(inspection.getFacility(), application, "INSPECTION_COMPLETED", "FACILITY_INSPECTION", inspectionId.toString(),
                null, Map.of("outcome", inspection.getOutcome(), "recommendation", inspection.getRecommendation()), ctx);
        publishEvent(inspection.getFacility(), "tuso.facility.inspection.completed", Map.of(
                "facilityId", inspection.getFacility().getId(),
                "applicationId", application != null ? application.getApplicationId() : null,
                "inspectionId", inspectionId,
                "outcome", inspection.getOutcome(),
                "severitySummary", inspection.getSeveritySummary(),
                "recommendation", inspection.getRecommendation()
        ), ctx);

        return toInspectionView(inspection);
    }

    @Transactional
    public FacilityRegulatoryDtos.FacilityRegulatoryProfileResponse updateComplianceAction(
            UUID actionId, FacilityRegulatoryDtos.UpdateComplianceActionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (request.status() == RectificationStatus.VERIFIED) {
            assertAllowed(ctx, INSPECTOR_ROLES);
        } else {
            assertAllowed(ctx, APPLICANT_ROLES);
        }

        ComplianceActionEntity action = complianceActionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Compliance action not found: " + actionId));
        if (!action.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation for action: " + actionId);
        }

        action.setStatus(request.status());
        action.setCompletionNotes(request.completionNotes());
        action.setCompletionEvidenceRefs(defaultList(request.completionEvidenceRefs()));
        action.setVerificationNotes(request.verificationNotes());
        action.setUpdatedBy(ctx.actorId());
        if (request.status() == RectificationStatus.VERIFIED) {
            action.setVerifiedBy(ctx.actorId());
            action.setVerifiedAt(Instant.now());
        }
        complianceActionRepository.save(action);

        InspectionFindingEntity finding = action.getInspectionFinding();
        finding.setRectificationStatus(request.status());
        findingRepository.save(finding);

        audit(action.getFacility(), action.getInspectionFinding().getInspection().getApplication(),
                "COMPLIANCE_ACTION_UPDATED", "COMPLIANCE_ACTION", actionId.toString(), null,
                Map.of("status", request.status().name()), ctx);
        publishEvent(action.getFacility(), "tuso.facility.compliance.action.updated", Map.of(
                "facilityId", action.getFacility().getId(),
                "actionId", actionId,
                "status", request.status().name()
        ), ctx);

        return getFacilityProfile(action.getFacility().getId());
    }

    @Transactional
    public FacilityRegulatoryDtos.CommitteeReviewView recordCommitteeDecision(
            FacilityRegulatoryDtos.RecordCommitteeDecisionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertAllowed(ctx, COMMITTEE_ROLES);

        FacilityEntity facility = requireFacility(request.facilityId(), ctx.tenantId());
        FacilityApplicationEntity application = requireApplication(request.applicationId(), ctx.tenantId());
        FacilityInspectionEntity inspection = request.inspectionId() != null
                ? inspectionRepository.findById(request.inspectionId())
                    .orElseThrow(() -> new IllegalArgumentException("Inspection not found: " + request.inspectionId()))
                : null;

        CommitteeReviewEntity review = new CommitteeReviewEntity();
        review.setTenantId(ctx.tenantId());
        review.setFacility(facility);
        review.setApplication(application);
        review.setInspection(inspection);
        review.setCommitteeType(request.committeeType());
        review.setScheduledDate(request.scheduledDate());
        review.setDecision(request.decision());
        review.setNotes(request.notes());
        review.setResolutionReference(request.resolutionReference());
        review.setAuthorityContext(firstNonBlank(request.authorityContext(), "HPA_COMMITTEE"));
        review.setDecidedBy(ctx.actorId());
        review.setMetadata(defaultMap(request.metadata()));
        review = committeeReviewRepository.save(review);

        application.setFinalDecision(request.decision().name());
        application.setDecisionDate(Instant.now());
        application.setCommitteeState(request.decision().name());
        application.setUpdatedBy(ctx.actorId());

        if (request.decision() == CommitteeDecision.APPROVED) {
            applyApprovedOutcome(facility, application, request, ctx);
            application.setCurrentWorkflowState(FacilityApplicationState.DECIDED_APPROVED);
        } else if (request.decision() == CommitteeDecision.REJECTED) {
            application.setCurrentWorkflowState(FacilityApplicationState.DECIDED_REJECTED);
            applyFacilityStatus(facility, FacilityRegulatoryStatus.RESTRICTED,
                    FacilityApplicationState.DECIDED_REJECTED, "Application rejected by committee",
                    application.getApplicationId(), ctx, review.getAuthorityContext());
        } else {
            application.setCurrentWorkflowState(FacilityApplicationState.DECIDED_DEFERRED);
            applyFacilityStatus(facility, FacilityRegulatoryStatus.PENDING_COMMITTEE_REVIEW,
                    FacilityApplicationState.DECIDED_DEFERRED, "Committee deferred application",
                    application.getApplicationId(), ctx, review.getAuthorityContext());
        }

        applicationRepository.save(application);

        audit(facility, application, "COMMITTEE_DECISION_RECORDED", "COMMITTEE_REVIEW", review.getReviewId().toString(),
                null, Map.of("decision", request.decision().name(), "authorityContext", review.getAuthorityContext()), ctx);
        publishEvent(facility, "tuso.facility.committee.decision.recorded", Map.of(
                "facilityId", facility.getId(),
                "applicationId", application.getApplicationId(),
                "reviewId", review.getReviewId(),
                "decision", request.decision().name()
        ), ctx);

        return toReviewView(review);
    }

    @Transactional
    public FacilityRegulatoryDtos.EnforcementCaseView openEnforcementCase(
            FacilityRegulatoryDtos.OpenEnforcementCaseRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertAllowed(ctx, INSPECTOR_ROLES);

        FacilityEntity facility = requireFacility(request.facilityId(), ctx.tenantId());
        FacilityApplicationEntity application = request.applicationId() != null ? requireApplication(request.applicationId(), ctx.tenantId()) : null;

        EnforcementCaseEntity enforcementCase = new EnforcementCaseEntity();
        enforcementCase.setTenantId(ctx.tenantId());
        enforcementCase.setFacility(facility);
        enforcementCase.setApplication(application);
        enforcementCase.setTriggerType(request.triggerType());
        enforcementCase.setStatus(request.status() != null ? request.status() : EnforcementCaseStatus.OPEN);
        enforcementCase.setRecommendation(request.recommendation());
        enforcementCase.setAuthorityRoute(request.authorityRoute());
        enforcementCase.setLinkedInspectionIds(defaultSimpleList(request.linkedInspectionIds()));
        enforcementCase.setLinkedEvidence(defaultList(request.linkedEvidence()));
        enforcementCase.setClosureReason(request.closureReason());
        enforcementCase.setDecisionDetails(request.decisionDetails());
        enforcementCase.setOpenedBy(ctx.actorId());
        enforcementCase.setUpdatedBy(ctx.actorId());
        enforcementCase = enforcementCaseRepository.save(enforcementCase);

        FacilityRegulatoryStatus nextStatus = switch (enforcementCase.getStatus()) {
            case RECOMMENDED_RESTRICTION -> FacilityRegulatoryStatus.RESTRICTED;
            case RECOMMENDED_SUSPENSION -> FacilityRegulatoryStatus.SUSPENDED;
            case RECOMMENDED_CLOSURE -> FacilityRegulatoryStatus.PENDING_CLOSURE;
            case CLOSED -> FacilityRegulatoryStatus.CLOSED;
            default -> FacilityRegulatoryStatus.RESTRICTED;
        };
        applyFacilityStatus(facility, nextStatus, application != null ? application.getCurrentWorkflowState() : null,
                "Enforcement case opened", application != null ? application.getApplicationId() : null, ctx, "HPA_ADMIN");

        audit(facility, application, "ENFORCEMENT_CASE_OPENED", "ENFORCEMENT_CASE", enforcementCase.getCaseId().toString(),
                null, Map.of("status", enforcementCase.getStatus().name(), "recommendation", nullSafe(enforcementCase.getRecommendation())), ctx);
        publishEvent(facility, "tuso.facility.enforcement.case.opened", Map.of(
                "facilityId", facility.getId(),
                "caseId", enforcementCase.getCaseId(),
                "status", enforcementCase.getStatus().name(),
                "recommendation", enforcementCase.getRecommendation()
        ), ctx);

        return toEnforcementView(enforcementCase);
    }

    @Transactional(readOnly = true)
    public FacilityRegulatoryDtos.DashboardSummaryResponse getDashboardSummary() {
        TrustContext ctx = TrustContextHolder.require();
        List<FacilityEntity> facilities = facilityRepository.findByTenantId(ctx.tenantId());
        List<ComplianceActionEntity> overdueActions = complianceActionRepository.findByStatusAndDueDateBefore(RectificationStatus.OPEN, LocalDate.now().plusDays(1));
        List<FacilityCertificateEntity> renewalsDue = certificateRepository.findByStatusAndExpiryBefore(
                FacilityCertificateStatus.ACTIVE, LocalDate.now().plusDays(90));
        long openCases = facilities.stream()
                .map(FacilityEntity::getId)
                .flatMap(id -> enforcementCaseRepository.findByFacilityIdOrderByOpenedAtDesc(id).stream())
                .filter(c -> c.getStatus() != EnforcementCaseStatus.CLOSED)
                .count();

        Map<String, Long> byStatus = facilities.stream()
                .collect(Collectors.groupingBy(f -> f.getRegulatoryStatus() != null
                                ? f.getRegulatoryStatus().name() : FacilityRegulatoryStatus.DRAFT.name(),
                        LinkedHashMap::new, Collectors.counting()));

        return new FacilityRegulatoryDtos.DashboardSummaryResponse(
                facilities.size(),
                facilities.stream().filter(f -> f.getRegulatoryStatus() == FacilityRegulatoryStatus.REGISTERED_ACTIVE).count(),
                facilities.stream().filter(f -> f.getRegulatoryStatus() == FacilityRegulatoryStatus.PENDING_INSPECTION).count(),
                facilities.stream().filter(f -> f.getRegulatoryStatus() == FacilityRegulatoryStatus.PENDING_COMMITTEE_REVIEW).count(),
                overdueActions.stream().filter(action -> action.getTenantId().equals(ctx.tenantId())).count(),
                renewalsDue.stream().filter(c -> c.getTenantId().equals(ctx.tenantId())).count(),
                openCases,
                byStatus
        );
    }

    private void applyApprovedOutcome(FacilityEntity facility,
                                      FacilityApplicationEntity application,
                                      FacilityRegulatoryDtos.RecordCommitteeDecisionRequest request,
                                      TrustContext ctx) {
        Map<String, Object> requestedChanges = defaultMap(application.getRequestedChanges());
        if (application.getApplicationType() == FacilityApplicationType.ADD_UNIT) {
            for (FacilityRegulatoryDtos.UnitDraft unitDraft : extractUnitDrafts(requestedChanges.get("requestedUnits"))) {
                FacilityUnitEntity unit = new FacilityUnitEntity();
                unit.setFacility(facility);
                unit.setName(unitDraft.name());
                unit.setUnitType(unitDraft.unitType());
                unit.setServiceLine(unitDraft.serviceLine());
                unit.setRegistrationRequired(unitDraft.registrationRequired());
                unit.setRegulatoryStatus(FacilityRegulatoryStatus.REGISTERED_ACTIVE);
                unit.setCertificateStatus("LINKED");
                unit.setMetadata(defaultMap(unitDraft.metadata()));
                unit.setCreatedBy(ctx.actorId());
                unit.setUpdatedBy(ctx.actorId());
                facilityUnitRepository.save(unit);
            }
        }

        FacilityRegulatoryDtos.PractitionerInChargeDraft practitionerDraft =
                extractPractitionerDraft(requestedChanges.get("practitionerInCharge"));
        if (practitionerDraft != null
                && (application.getApplicationType() == FacilityApplicationType.CHANGE_OF_PRACTITIONER_IN_CHARGE
                || application.getApplicationType() == FacilityApplicationType.NEW_REGISTRATION)) {
            PractitionerInChargeAssignmentEntity assignment = new PractitionerInChargeAssignmentEntity();
            assignment.setFacility(facility);
            assignment.setProviderPublicId(practitionerDraft.providerPublicId());
            assignment.setRole(firstNonBlank(practitionerDraft.role(), "PRACTITIONER_IN_CHARGE"));
            assignment.setStartDate(practitionerDraft.startDate());
            assignment.setApprovalState("APPROVED");
            assignment.setSourceCouncilReference(practitionerDraft.sourceCouncilReference());
            assignment.setNotes(practitionerDraft.notes());
            assignment.setCreatedBy(ctx.actorId());
            assignment.setUpdatedBy(ctx.actorId());
            practitionerRepository.save(assignment);
        }

        applyRequestedFacilityUpdates(facility, requestedChanges, ctx);

        if (!Boolean.FALSE.equals(request.issueCertificate())) {
            issueCertificate(facility, application, request, ctx);
        }

        FacilityRegulatoryStatus approvedStatus = application.getApplicationType() == FacilityApplicationType.VOLUNTARY_CLOSURE_NOTICE
                ? FacilityRegulatoryStatus.VOLUNTARILY_CLOSED
                : FacilityRegulatoryStatus.REGISTERED_ACTIVE;
        if (approvedStatus == FacilityRegulatoryStatus.VOLUNTARILY_CLOSED) {
            facility.setStatus("CLOSED");
            facility.setClosedDate(LocalDate.now());
            facility.setCloseReason("Voluntary closure recorded through HPA workflow");
        } else {
            facility.setStatus("ACTIVE");
        }
        applyFacilityStatus(facility, approvedStatus, FacilityApplicationState.DECIDED_APPROVED,
                "Application approved by committee", application.getApplicationId(), ctx,
                firstNonBlank(request.authorityContext(), "HPA_COMMITTEE"));
    }

    private void issueCertificate(FacilityEntity facility,
                                  FacilityApplicationEntity application,
                                  FacilityRegulatoryDtos.RecordCommitteeDecisionRequest request,
                                  TrustContext ctx) {
        FacilityCertificateEntity superseded = null;
        if (application.getApplicationType() == FacilityApplicationType.RENEWAL) {
            superseded = certificateRepository.findFirstByFacilityIdAndStatusOrderByIssueDateDesc(
                    facility.getId(), FacilityCertificateStatus.ACTIVE).orElse(null);
            if (superseded != null) {
                superseded.setStatus(FacilityCertificateStatus.SUPERSEDED);
                certificateRepository.save(superseded);
            }
        }

        FacilityCertificateEntity certificate = new FacilityCertificateEntity();
        certificate.setTenantId(ctx.tenantId());
        certificate.setFacility(facility);
        certificate.setApplication(application);
        certificate.setCertificateType(firstNonBlank(request.certificateType(), "HEALTH_INSTITUTION_REGISTRATION"));
        certificate.setCertificateNumber(generateCertificateNumber(facility, application));
        certificate.setIssueDate(LocalDate.now());
        certificate.setExpiryDate(LocalDate.now().plusYears(1));
        certificate.setStatus(FacilityCertificateStatus.ACTIVE);
        certificate.setIssuedUnderAuthority(firstNonBlank(request.authorityContext(), "HPA"));
        certificate.setDigitalArtifactReference(request.digitalArtifactReference());
        certificate.setSupersedesCertificate(superseded);
        certificate.setMetadata(defaultMap(request.metadata()));
        certificate.setIssuedBy(ctx.actorId());
        certificate = certificateRepository.save(certificate);

        publishEvent(facility, "tuso.facility.certificate.issued", Map.of(
                "facilityId", facility.getId(),
                "applicationId", application.getApplicationId(),
                "certificateId", certificate.getCertificateId(),
                "certificateNumber", certificate.getCertificateNumber(),
                "expiryDate", certificate.getExpiryDate()
        ), ctx);
    }

    private void applyRequestedFacilityUpdates(FacilityEntity facility, Map<String, Object> requestedChanges, TrustContext ctx) {
        if (requestedChanges.isEmpty()) {
            return;
        }

        facility.setName(stringOrCurrent(requestedChanges.get("facilityName"), facility.getName()));
        facility.setFacilityCode(stringOrCurrent(requestedChanges.get("facilityCode"), facility.getFacilityCode()));
        facility.setOwnership(stringOrCurrent(requestedChanges.get("ownershipType"), facility.getOwnership()));
        facility.setOperatingEntity(stringOrCurrent(requestedChanges.get("operatingEntity"), facility.getOperatingEntity()));
        facility.setFacilityClass(stringOrCurrent(requestedChanges.get("facilityClass"), facility.getFacilityClass()));
        facility.setFacilityCategory(stringOrCurrent(requestedChanges.get("facilityCategory"), facility.getFacilityCategory()));
        facility.setFacilityType(stringOrCurrent(requestedChanges.get("facilityType"), facility.getFacilityType()));
        facility.setLegalStatus(stringOrCurrent(requestedChanges.get("legalStatus"), facility.getLegalStatus()));
        facility.setRegistrationPathway(stringOrCurrent(requestedChanges.get("registrationPathway"), facility.getRegistrationPathway()));
        facility.setProvince(stringOrCurrent(requestedChanges.get("province"), facility.getProvince()));
        facility.setDistrict(stringOrCurrent(requestedChanges.get("district"), facility.getDistrict()));
        facility.setMetadata(defaultMap(castMap(requestedChanges.get("metadata"), facility.getMetadata())));
        if (requestedChanges.get("aliasNames") instanceof List<?> aliasList) {
            facility.setAliasNames(aliasList.stream().map(String::valueOf).toList());
        }
        if (requestedChanges.get("latitude") instanceof Number latitude) {
            facility.setLatitude(new java.math.BigDecimal(latitude.toString()));
        }
        if (requestedChanges.get("longitude") instanceof Number longitude) {
            facility.setLongitude(new java.math.BigDecimal(longitude.toString()));
        }
        facility.setUpdatedBy(ctx.actorId());
        facilityRepository.save(facility);

        FacilityGeoEntity geo = facilityGeoRepository.findByFacilityId(facility.getId()).orElseGet(() -> {
            FacilityGeoEntity entity = new FacilityGeoEntity();
            entity.setFacility(facility);
            return entity;
        });
        geo.setAddressLine1(stringOrCurrent(requestedChanges.get("addressLine1"), geo.getAddressLine1()));
        geo.setAddressLine2(stringOrCurrent(requestedChanges.get("addressLine2"), geo.getAddressLine2()));
        geo.setCity(stringOrCurrent(requestedChanges.get("city"), geo.getCity()));
        geo.setProvince(stringOrCurrent(requestedChanges.get("province"), geo.getProvince()));
        geo.setDistrict(stringOrCurrent(requestedChanges.get("district"), geo.getDistrict()));
        geo.setWard(stringOrCurrent(requestedChanges.get("ward"), geo.getWard()));
        geo.setPostalCode(stringOrCurrent(requestedChanges.get("postalCode"), geo.getPostalCode()));
        geo.setCountry(firstNonBlank(geo.getCountry(), "ZWE"));
        facilityGeoRepository.save(geo);
    }

    private FacilityEntity createApplicationFacility(FacilityRegulatoryDtos.CreateFacilityApplicationRequest request, TrustContext ctx) {
        if (request.facilityName() == null || request.facilityName().isBlank()) {
            throw new IllegalArgumentException("Facility name is required for new applications");
        }

        FacilityEntity facility = new FacilityEntity();
        facility.setTenantId(ctx.tenantId());
        facility.setFacilityCode(firstNonBlank(request.facilityCode(), generateFacilityCode(request.facilityName())));
        facility.setName(request.facilityName());
        facility.setAliasNames(defaultSimpleList(request.aliasNames()));
        facility.setOwnership(request.ownershipType());
        facility.setOperatingEntity(request.operatingEntity());
        facility.setFacilityClass(request.facilityClass());
        facility.setFacilityCategory(request.facilityCategory());
        facility.setFacilityType(request.facilityType());
        facility.setLegalStatus(request.legalStatus());
        facility.setRegistrationPathway(firstNonBlank(request.registrationPathway(), request.pathway()));
        facility.setProvince(request.province());
        facility.setDistrict(request.district());
        facility.setLatitude(request.latitude());
        facility.setLongitude(request.longitude());
        facility.setStatus("PROVISIONAL");
        facility.setOperationalStatus("PRE_OPERATIONAL");
        facility.setRegulatoryStatus(FacilityRegulatoryStatus.APPLICATION_IN_PROGRESS);
        facility.setRegulatoryStatusUpdatedAt(Instant.now());
        facility.setInstitutionFileNumber(generateInstitutionFileNumber(null));
        facility.setMetadata(defaultMap(request.metadata()));
        facility.setCreatedBy(ctx.actorId());
        facility.setUpdatedBy(ctx.actorId());
        facility = facilityRepository.save(facility);

        FacilityGeoEntity geo = new FacilityGeoEntity();
        geo.setFacility(facility);
        geo.setAddressLine1(request.addressLine1());
        geo.setAddressLine2(request.addressLine2());
        geo.setCity(request.city());
        geo.setProvince(request.province());
        geo.setDistrict(request.district());
        geo.setWard(request.ward());
        geo.setPostalCode(request.postalCode());
        geo.setCountry("ZWE");
        facilityGeoRepository.save(geo);

        return facility;
    }

    private void validateApplicationSubmission(FacilityApplicationEntity application) {
        if (application.getApplicantName() == null || application.getApplicantName().isBlank()) {
            throw new IllegalStateException("Applicant name is required");
        }
        if (application.getFacility() == null || application.getFacility().getName() == null || application.getFacility().getName().isBlank()) {
            throw new IllegalStateException("Facility master details are incomplete");
        }
        if (documentRepository.findByApplicationApplicationIdOrderByUploadedAtDesc(application.getApplicationId()).isEmpty()) {
            throw new IllegalStateException("At least one supporting document is required before submission");
        }
    }

    private InspectionChecklistTemplateEntity resolveTemplate(String templateCode,
                                                              FacilityInspectionType inspectionType,
                                                              String facilityType) {
        if (templateCode != null && !templateCode.isBlank()) {
            return checklistTemplateRepository.findByCode(templateCode)
                    .orElseThrow(() -> new IllegalArgumentException("Checklist template not found: " + templateCode));
        }
        return checklistTemplateRepository.findApplicableTemplates(inspectionType, facilityType)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No checklist template configured for inspectionType=" + inspectionType + ", facilityType=" + facilityType));
    }

    private Map<String, Map<String, Object>> indexTemplateItems(InspectionChecklistTemplateEntity template) {
        if (template == null || template.getItemsJson() == null) {
            return Map.of();
        }
        Map<String, Map<String, Object>> items = new HashMap<>();
        for (Map<String, Object> item : template.getItemsJson()) {
            Object code = item.get("code");
            if (code != null) {
                items.put(String.valueOf(code), item);
            }
        }
        return items;
    }

    private void applyFacilityStatus(FacilityEntity facility, FacilityRegulatoryStatus status,
                                     FacilityApplicationState applicationState, String reason,
                                     UUID sourceApplicationId, TrustContext ctx, String authorityContext) {
        facility.setRegulatoryStatus(status);
        facility.setRegulatoryStatusUpdatedAt(Instant.now());
        facility.setUpdatedBy(ctx.actorId());
        facilityRepository.save(facility);

        FacilityStatusHistoryEntity history = new FacilityStatusHistoryEntity();
        history.setFacility(facility);
        history.setRegulatoryStatus(status);
        history.setApplicationState(applicationState);
        history.setChangedBy(ctx.actorId());
        history.setAuthorityContext(authorityContext);
        history.setReason(reason);
        history.setSourceApplicationId(sourceApplicationId);
        statusHistoryRepository.save(history);
    }

    private void audit(FacilityEntity facility, FacilityApplicationEntity application, String action,
                       String targetEntityType, String targetEntityId, Map<String, Object> beforeState,
                       Map<String, Object> afterState, TrustContext ctx) {
        FacilityAuditEventEntity audit = new FacilityAuditEventEntity();
        audit.setTenantId(ctx.tenantId());
        audit.setFacility(facility);
        audit.setApplication(application);
        audit.setActorId(ctx.actorId());
        audit.setActorType(ctx.actorType());
        audit.setAuthorityContext(firstNonBlank(ctx.actorType(), "UNSPECIFIED_AUTHORITY"));
        audit.setAction(action);
        audit.setTargetEntityType(targetEntityType);
        audit.setTargetEntityId(targetEntityId);
        audit.setBeforeState(beforeState);
        audit.setAfterState(afterState);
        audit.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        auditEventRepository.save(audit);
    }

    private void publishEvent(FacilityEntity facility, String eventType, Map<String, Object> payload, TrustContext ctx) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("FACILITY");
        outbox.setAggregateId(String.valueOf(facility.getId()));
        outbox.setEventType(eventType);
        outbox.setPayload(payload);
        outbox.setTenantId(ctx.tenantId().toString());
        outbox.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        outbox.setCausationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        outbox.setProducer("tuso");
        outbox.setSubjectType("Facility");
        outbox.setSubjectId(String.valueOf(facility.getId()));
        outbox.setPartitionKey(String.valueOf(facility.getId()));
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(Instant.now().atOffset(java.time.ZoneOffset.UTC));
        outboxRepository.save(outbox);
    }

    private FacilityEntity requireFacility(Long facilityId, UUID tenantId) {
        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));
        if (!facility.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation for facility: " + facilityId);
        }
        return facility;
    }

    private FacilityApplicationEntity requireApplication(UUID applicationId, UUID tenantId) {
        FacilityApplicationEntity application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Facility application not found: " + applicationId));
        if (!application.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation for application: " + applicationId);
        }
        return application;
    }

    private void assertAllowed(TrustContext ctx, Set<String> allowedRoles) {
        if (ctx.actorType() == null || ctx.actorType().isBlank()) {
            return;
        }
        if (!allowedRoles.contains(ctx.actorType())) {
            throw new SecurityException("Actor type " + ctx.actorType() + " cannot perform this facility lifecycle action");
        }
    }

    private Map<String, Object> buildRequestedChanges(FacilityRegulatoryDtos.CreateFacilityApplicationRequest request) {
        Map<String, Object> changes = new LinkedHashMap<>();
        putIfPresent(changes, "facilityCode", request.facilityCode());
        putIfPresent(changes, "facilityName", request.facilityName());
        putIfPresent(changes, "aliasNames", request.aliasNames());
        putIfPresent(changes, "ownershipType", request.ownershipType());
        putIfPresent(changes, "operatingEntity", request.operatingEntity());
        putIfPresent(changes, "facilityClass", request.facilityClass());
        putIfPresent(changes, "facilityCategory", request.facilityCategory());
        putIfPresent(changes, "facilityType", request.facilityType());
        putIfPresent(changes, "legalStatus", request.legalStatus());
        putIfPresent(changes, "registrationPathway", request.registrationPathway());
        putIfPresent(changes, "province", request.province());
        putIfPresent(changes, "district", request.district());
        putIfPresent(changes, "city", request.city());
        putIfPresent(changes, "ward", request.ward());
        putIfPresent(changes, "postalCode", request.postalCode());
        putIfPresent(changes, "addressLine1", request.addressLine1());
        putIfPresent(changes, "addressLine2", request.addressLine2());
        putIfPresent(changes, "latitude", request.latitude());
        putIfPresent(changes, "longitude", request.longitude());
        putIfPresent(changes, "metadata", request.metadata());
        putIfPresent(changes, "requestedUnits", request.requestedUnits());
        putIfPresent(changes, "practitionerInCharge", request.practitionerInCharge());
        return changes;
    }

    private FacilityRegulatoryDtos.FacilityRegistrySummaryResponse toSummary(FacilityEntity facility) {
        FacilityCertificateEntity latestCertificate = certificateRepository
                .findFirstByFacilityIdAndStatusOrderByIssueDateDesc(facility.getId(), FacilityCertificateStatus.ACTIVE)
                .orElse(null);
        long openApplications = applicationRepository.findByFacilityIdOrderByCreatedAtDesc(facility.getId())
                .stream()
                .filter(a -> switch (a.getCurrentWorkflowState()) {
                    case DECIDED_APPROVED, DECIDED_REJECTED, CLOSED_OUT -> false;
                    default -> true;
                })
                .count();
        long overdueActions = complianceActionRepository.findByFacilityIdOrderByCreatedAtDesc(facility.getId())
                .stream()
                .filter(a -> (a.getStatus() == RectificationStatus.OPEN || a.getStatus() == RectificationStatus.IN_PROGRESS)
                        && a.getDueDate() != null && a.getDueDate().isBefore(LocalDate.now()))
                .count();
        return new FacilityRegulatoryDtos.FacilityRegistrySummaryResponse(
                facility.getId(),
                facility.getFacilityCode(),
                facility.getName(),
                facility.getFacilityType(),
                facility.getStatus(),
                facility.getRegulatoryStatus(),
                facility.getRegistrationPathway(),
                facility.getProvince(),
                facility.getDistrict(),
                facility.getInstitutionFileNumber(),
                latestCertificate != null ? latestCertificate.getCertificateNumber() : null,
                latestCertificate != null ? latestCertificate.getExpiryDate() : null,
                openApplications,
                overdueActions
        );
    }

    private FacilityRegulatoryDtos.FacilityMaster toMaster(FacilityEntity facility, FacilityGeoEntity geo) {
        return new FacilityRegulatoryDtos.FacilityMaster(
                facility.getId(),
                facility.getFacilityCode(),
                facility.getName(),
                defaultSimpleList(facility.getAliasNames()),
                facility.getOwnership(),
                facility.getOperatingEntity(),
                facility.getFacilityClass(),
                facility.getFacilityCategory(),
                facility.getFacilityType(),
                facility.getLegalStatus(),
                facility.getRegistrationPathway(),
                facility.getStatus(),
                facility.getOperationalStatus(),
                facility.getRegulatoryStatus(),
                facility.getInstitutionFileNumber(),
                facility.getProvince(),
                facility.getDistrict(),
                geo != null ? geo.getCity() : null,
                geo != null ? geo.getWard() : null,
                geo != null ? geo.getPostalCode() : null,
                geo != null ? geo.getAddressLine1() : null,
                geo != null ? geo.getAddressLine2() : null,
                facility.getLatitude(),
                facility.getLongitude(),
                facility.getMetadata(),
                facility.getCreatedAt(),
                facility.getUpdatedAt()
        );
    }

    private FacilityRegulatoryDtos.ApplicationView toApplicationView(FacilityApplicationEntity entity) {
        return new FacilityRegulatoryDtos.ApplicationView(
                entity.getApplicationId(),
                entity.getApplicationType(),
                entity.getPathway(),
                entity.getApplicantName(),
                entity.getSubmissionDate(),
                entity.getCurrentWorkflowState(),
                entity.getPriority(),
                entity.getFeeState(),
                entity.getCommitteeState(),
                entity.getFinalDecision(),
                entity.getDecisionDate(),
                entity.getInstitutionFileNumber(),
                entity.getRequestedChanges(),
                entity.getMetadata(),
                entity.getWorkflowNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private FacilityRegulatoryDtos.DocumentView toDocumentView(FacilityDocumentEntity entity) {
        return new FacilityRegulatoryDtos.DocumentView(
                entity.getDocumentId(),
                entity.getDocumentType(),
                entity.getFileReference(),
                entity.getVersion(),
                entity.getVerificationState(),
                entity.getUploadedBy(),
                entity.getUploadedAt(),
                entity.getNotes(),
                entity.getMetadata()
        );
    }

    private FacilityRegulatoryDtos.InspectionView toInspectionView(FacilityInspectionEntity entity) {
        return new FacilityRegulatoryDtos.InspectionView(
                entity.getInspectionId(),
                entity.getApplication() != null ? entity.getApplication().getApplicationId() : null,
                entity.getInspectionType(),
                entity.getTemplate() != null ? entity.getTemplate().getCode() : null,
                entity.getTemplate() != null ? entity.getTemplate().getName() : null,
                entity.getScheduledDate(),
                entity.getActualDate(),
                defaultList(entity.getInspectorAssignments()),
                entity.getStatus(),
                entity.getReportStatus(),
                entity.getOutcome(),
                entity.getRecommendation(),
                entity.getSeveritySummary(),
                entity.getNextAction(),
                entity.getNextInspectionDueDate(),
                entity.getCaptureMode(),
                entity.getOfflineCaptureReference(),
                entity.getEvidenceSummary(),
                entity.getNotes()
        );
    }

    private FacilityRegulatoryDtos.FindingView toFindingView(InspectionFindingEntity entity) {
        return new FacilityRegulatoryDtos.FindingView(
                entity.getFindingId(),
                entity.getInspection().getInspectionId(),
                entity.getChecklistItemCode(),
                entity.getChecklistSection(),
                entity.getRequirementText(),
                entity.isCriticalFlag(),
                entity.getStatus(),
                entity.getSeverity(),
                entity.getComments(),
                defaultList(entity.getEvidenceRefs()),
                entity.getRectificationDeadline(),
                entity.getRectificationStatus()
        );
    }

    private FacilityRegulatoryDtos.ComplianceActionView toComplianceActionView(ComplianceActionEntity entity) {
        return new FacilityRegulatoryDtos.ComplianceActionView(
                entity.getActionId(),
                entity.getInspectionFinding().getFindingId(),
                entity.getActionType(),
                entity.getOwnerName(),
                entity.getOwnerRole(),
                entity.getDueDate(),
                entity.getStatus(),
                entity.getCompletionNotes(),
                defaultList(entity.getCompletionEvidenceRefs()),
                entity.getVerifiedBy(),
                entity.getVerifiedAt(),
                entity.getVerificationNotes()
        );
    }

    private FacilityRegulatoryDtos.CommitteeReviewView toReviewView(CommitteeReviewEntity entity) {
        return new FacilityRegulatoryDtos.CommitteeReviewView(
                entity.getReviewId(),
                entity.getApplication() != null ? entity.getApplication().getApplicationId() : null,
                entity.getInspection() != null ? entity.getInspection().getInspectionId() : null,
                entity.getCommitteeType(),
                entity.getScheduledDate(),
                entity.getDecision(),
                entity.getNotes(),
                entity.getResolutionReference(),
                entity.getAuthorityContext(),
                entity.getDecidedBy(),
                entity.getDecidedAt(),
                entity.getMetadata()
        );
    }

    private FacilityRegulatoryDtos.CertificateView toCertificateView(FacilityCertificateEntity entity) {
        return new FacilityRegulatoryDtos.CertificateView(
                entity.getCertificateId(),
                entity.getApplication() != null ? entity.getApplication().getApplicationId() : null,
                entity.getCertificateType(),
                entity.getCertificateNumber(),
                entity.getIssueDate(),
                entity.getExpiryDate(),
                entity.getStatus(),
                entity.getIssuedUnderAuthority(),
                entity.getDigitalArtifactReference(),
                entity.getSupersedesCertificate() != null ? entity.getSupersedesCertificate().getCertificateId() : null,
                entity.getMetadata(),
                entity.getCreatedAt(),
                entity.getIssuedBy()
        );
    }

    private FacilityRegulatoryDtos.UnitView toUnitView(FacilityUnitEntity entity) {
        return new FacilityRegulatoryDtos.UnitView(
                entity.getId(),
                entity.getName(),
                entity.getUnitType(),
                entity.getServiceLine(),
                entity.isRegistrationRequired(),
                entity.getRegulatoryStatus(),
                entity.getCertificateStatus(),
                entity.getMetadata()
        );
    }

    private FacilityRegulatoryDtos.PractitionerAssignmentView toPractitionerView(PractitionerInChargeAssignmentEntity entity) {
        return new FacilityRegulatoryDtos.PractitionerAssignmentView(
                entity.getId(),
                entity.getProviderPublicId(),
                entity.getRole(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getApprovalState(),
                entity.getSourceCouncilReference(),
                entity.getNotes()
        );
    }

    private FacilityRegulatoryDtos.StatusHistoryView toStatusHistoryView(FacilityStatusHistoryEntity entity) {
        return new FacilityRegulatoryDtos.StatusHistoryView(
                entity.getChangedAt(),
                entity.getRegulatoryStatus(),
                entity.getApplicationState(),
                entity.getChangedBy(),
                entity.getAuthorityContext(),
                entity.getReason(),
                entity.getSourceApplicationId()
        );
    }

    private FacilityRegulatoryDtos.AuditEventView toAuditView(FacilityAuditEventEntity entity) {
        return new FacilityRegulatoryDtos.AuditEventView(
                entity.getAuditId(),
                entity.getCreatedAt(),
                entity.getActorId(),
                entity.getActorType(),
                entity.getAuthorityContext(),
                entity.getAction(),
                entity.getTargetEntityType(),
                entity.getTargetEntityId(),
                entity.getBeforeState(),
                entity.getAfterState(),
                entity.getCorrelationId(),
                entity.getMetadata()
        );
    }

    private FacilityRegulatoryDtos.EnforcementCaseView toEnforcementView(EnforcementCaseEntity entity) {
        return new FacilityRegulatoryDtos.EnforcementCaseView(
                entity.getCaseId(),
                entity.getApplication() != null ? entity.getApplication().getApplicationId() : null,
                entity.getTriggerType(),
                entity.getStatus(),
                entity.getRecommendation(),
                entity.getAuthorityRoute(),
                defaultSimpleList(entity.getLinkedInspectionIds()),
                defaultList(entity.getLinkedEvidence()),
                entity.getClosureReason(),
                entity.getDecisionDetails(),
                entity.getOpenedAt(),
                entity.getClosedAt(),
                entity.getOpenedBy(),
                entity.getUpdatedBy()
        );
    }

    private String generateInstitutionFileNumber(FacilityEntity facility) {
        String seed = facility != null && facility.getFacilityCode() != null ? facility.getFacilityCode() : UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return "HIF-" + LocalDate.now().getYear() + "-" + seed.replaceAll("[^A-Z0-9]", "").substring(0, Math.min(seed.replaceAll("[^A-Z0-9]", "").length(), 8));
    }

    private String generateFacilityCode(String facilityName) {
        String slug = facilityName.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (slug.length() > 10) {
            slug = slug.substring(0, 10);
        }
        return "TUSO-" + slug + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private String generateCertificateNumber(FacilityEntity facility, FacilityApplicationEntity application) {
        return "HPA-" + LocalDate.now().getYear() + "-" + facility.getFacilityCode() + "-" +
                application.getApplicationType().name().substring(0, Math.min(4, application.getApplicationType().name().length()));
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String nullSafe(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private String stringValue(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) {
            return null;
        }
        return String.valueOf(map.get(key));
    }

    private boolean booleanValue(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) {
            return false;
        }
        Object value = map.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value, Map<String, Object> fallback) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().collect(Collectors.toMap(
                    e -> String.valueOf(e.getKey()),
                    Map.Entry::getValue,
                    (left, right) -> right,
                    LinkedHashMap::new));
        }
        return fallback;
    }

    private String stringOrCurrent(Object value, String current) {
        return value != null ? String.valueOf(value) : current;
    }

    private List<Map<String, Object>> defaultList(List<?> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> objectMapper.convertValue(value, Map.class))
                .map(map -> (Map<String, Object>) map)
                .toList();
    }

    private List<String> defaultSimpleList(List<?> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }

    private Map<String, Object> defaultMap(Map<String, Object> value) {
        return value != null ? new LinkedHashMap<>(value) : new LinkedHashMap<>();
    }

    private List<FacilityRegulatoryDtos.UnitDraft> extractUnitDrafts(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return list.stream()
                    .map(item -> objectMapper.convertValue(item, FacilityRegulatoryDtos.UnitDraft.class))
                    .toList();
        }
        return List.of();
    }

    private FacilityRegulatoryDtos.PractitionerInChargeDraft extractPractitionerDraft(Object raw) {
        if (raw == null) {
            return null;
        }
        return objectMapper.convertValue(raw, FacilityRegulatoryDtos.PractitionerInChargeDraft.class);
    }
}
