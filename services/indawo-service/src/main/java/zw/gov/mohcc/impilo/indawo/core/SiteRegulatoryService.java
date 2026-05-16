package zw.gov.mohcc.impilo.indawo.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.indawo.api.dto.SiteRegulatoryDtos;
import zw.gov.mohcc.impilo.indawo.domain.*;
import zw.gov.mohcc.impilo.indawo.repository.*;
import zw.gov.mohcc.impilo.indawo.integration.DocumentStoreClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SiteRegulatoryService {

    private static final Logger log = LoggerFactory.getLogger(SiteRegulatoryService.class);
    private static final String PRODUCER = "indawo-service";

    private final SiteRepository siteRepository;
    private final SiteApplicationRepository applicationRepository;
    private final SiteOperatorRepository operatorRepository;
    private final InspectionChecklistTemplateRepository templateRepository;
    private final InspectionChecklistItemRepository itemRepository;
    private final SiteInspectionRepository inspectionRepository;
    private final SiteInspectionFindingRepository findingRepository;
    private final SiteComplianceActionRepository actionRepository;
    private final SiteLicenceRepository licenceRepository;
    private final SiteEnforcementCaseRepository caseRepository;
    private final SiteDocumentRepository documentRepository;
    private final SiteAssignmentRepository assignmentRepository;
    private final SiteStatusHistoryRepository statusHistoryRepository;
    private final AuditEventRepository auditRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final DocumentStoreClient documentStoreClient;

    public SiteRegulatoryService(
            SiteRepository siteRepository,
            SiteApplicationRepository applicationRepository,
            SiteOperatorRepository operatorRepository,
            InspectionChecklistTemplateRepository templateRepository,
            InspectionChecklistItemRepository itemRepository,
            SiteInspectionRepository inspectionRepository,
            SiteInspectionFindingRepository findingRepository,
            SiteComplianceActionRepository actionRepository,
            SiteLicenceRepository licenceRepository,
            SiteEnforcementCaseRepository caseRepository,
            SiteDocumentRepository documentRepository,
            SiteAssignmentRepository assignmentRepository,
            SiteStatusHistoryRepository statusHistoryRepository,
            AuditEventRepository auditRepository,
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper,
            DocumentStoreClient documentStoreClient
    ) {
        this.siteRepository = siteRepository;
        this.applicationRepository = applicationRepository;
        this.operatorRepository = operatorRepository;
        this.templateRepository = templateRepository;
        this.itemRepository = itemRepository;
        this.inspectionRepository = inspectionRepository;
        this.findingRepository = findingRepository;
        this.actionRepository = actionRepository;
        this.licenceRepository = licenceRepository;
        this.caseRepository = caseRepository;
        this.documentRepository = documentRepository;
        this.assignmentRepository = assignmentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.auditRepository = auditRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.documentStoreClient = documentStoreClient;
    }

    @Transactional(readOnly = true)
    public Page<SiteRegulatoryDtos.SiteRegistrySummaryResponse> searchSites(
            String search, String regulatoryStatus, String siteCategory, String province, String district, int page, int size
    ) {
        RequestContext ctx = RequestContextHolder.require();
        var result = siteRepository.searchSites(
                UUID.fromString(ctx.tenantId()),
                normalize(search),
                normalize(regulatoryStatus),
                normalize(siteCategory),
                normalize(province),
                normalize(district),
                PageRequest.of(page, size));

        return result.map(s -> {
            var licences = licenceRepository.findBySiteIdOrderByExpiryDateDesc(s.getSiteId());
            SiteLicenceEntity latest = licences.isEmpty() ? null : licences.getFirst();
            return new SiteRegulatoryDtos.SiteRegistrySummaryResponse(
                    s.getSiteId(),
                    s.getSiteCode(),
                    s.getName(),
                    s.getType(),
                    s.getSiteCategory(),
                    s.getProvince(),
                    s.getDistrict(),
                    s.getRegulatoryStatus(),
                    s.getLifecycleStatus(),
                    latest != null ? latest.getStatus() : null,
                    latest != null ? latest.getExpiryDate() : null
            );
        });
    }

    @Transactional(readOnly = true)
    public SiteRegulatoryDtos.SiteRegulatoryProfileResponse getSiteProfile(UUID siteId) {
        RequestContextHolder.require();
        SiteEntity site = siteRepository.findById(siteId).orElseThrow(() -> new IllegalArgumentException("Site not found"));

        List<SiteApplicationEntity> apps = applicationRepository.findBySiteIdOrderByCreatedAtDesc(siteId);
        List<SiteOperatorEntity> ops = operatorRepository.findBySiteIdOrderByUpdatedAtDesc(siteId);
        List<SiteInspectionEntity> inspections = inspectionRepository.findBySiteIdOrderByCreatedAtDesc(siteId);
        List<SiteComplianceActionEntity> actions = actionRepository.findBySiteIdOrderByUpdatedAtDesc(siteId);
        List<SiteDocumentEntity> documents = documentRepository.findBySiteIdOrderByUploadedAtDesc(siteId);
        List<SiteLicenceEntity> licences = licenceRepository.findBySiteIdOrderByExpiryDateDesc(siteId);
        List<SiteEnforcementCaseEntity> cases = caseRepository.findBySiteIdOrderByOpenedAtDesc(siteId);
        List<SiteAssignmentEntity> assignments = assignmentRepository.findBySiteIdOrderByUpdatedAtDesc(siteId);
        List<SiteStatusHistoryEntity> statusHistory = statusHistoryRepository.findBySiteIdOrderByChangedAtDesc(siteId);
        List<AuditEventEntity> audit = auditRepository.findBySiteIdOrderByCreatedAtDesc(siteId);

        // Findings span inspections
        List<SiteInspectionFindingEntity> findings = new ArrayList<>();
        for (SiteInspectionEntity insp : inspections) {
            findings.addAll(findingRepository.findByInspectionId(insp.getInspectionId()));
        }

        return new SiteRegulatoryDtos.SiteRegulatoryProfileResponse(
                toSiteView(site),
                apps.stream().map(this::toApplicationView).toList(),
                ops.stream().map(this::toOperatorView).toList(),
                inspections.stream().map(this::toInspectionView).toList(),
                findings.stream().map(this::toFindingView).toList(),
                actions.stream().map(this::toComplianceView).toList(),
                documents.stream().map(this::toDocumentView).toList(),
                licences.stream().map(this::toLicenceView).toList(),
                cases.stream().map(this::toCaseView).toList(),
                assignments.stream().map(this::toAssignmentView).toList(),
                statusHistory.stream().map(this::toStatusHistoryView).toList(),
                audit.stream().map(this::toAuditView).toList()
        );
    }

    @Transactional
    public SiteRegulatoryDtos.SiteRegulatoryProfileResponse createApplication(SiteRegulatoryDtos.CreateSiteApplicationRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        String actor = actorId();

        UUID siteId = request.siteId();
        SiteEntity site;
        if (siteId == null) {
            if (request.siteDraft() == null) throw new IllegalArgumentException("siteDraft is required when siteId is null");
            siteId = UUID.randomUUID();
            site = new SiteEntity(siteId, tenantId, request.siteDraft().name(), request.siteDraft().type());
            applySiteDraft(site, request.siteDraft());
            site.setLifecycleStatus(SiteLifecycleStatus.DRAFT.name());
            site.setRegulatoryStatus(SiteRegulatoryStatus.APPLICATION_IN_PROGRESS.name());
            siteRepository.save(site);
            audit(siteId, "SITE_CREATED", "Site", siteId.toString(), null, toJsonSafe(toSiteView(site)));
            emit("impilo.indawo.site.registry.created.v1", "Site", siteId.toString(), siteId.toString(), Map.of("site_id", siteId));
        } else {
            site = siteRepository.findById(siteId).orElseThrow(() -> new IllegalArgumentException("Site not found"));
        }

        UUID appId = UUID.randomUUID();
        SiteApplicationEntity app = new SiteApplicationEntity(appId, tenantId, request.applicationType(), request.applicantName());
        app.setSiteId(siteId);
        app.setApplicantEmail(request.applicantEmail());
        app.setApplicantPhone(request.applicantPhone());
        app.setApplicantOrg(request.applicantOrg());
        app.setNotes(request.notes());
        app.setCreatedBy(actor);
        app.setUpdatedBy(actor);
        applicationRepository.save(app);

        if (request.operatorDraft() != null) {
            UUID opId = UUID.randomUUID();
            SiteOperatorEntity operator = new SiteOperatorEntity(opId, tenantId, siteId,
                    request.operatorDraft().operatorType(), request.operatorDraft().operatorName());
            operator.setContactEmail(request.operatorDraft().contactEmail());
            operator.setContactPhone(request.operatorDraft().contactPhone());
            operatorRepository.save(operator);
            site.setOperatorName(operator.getOperatorName());
            siteRepository.save(site);
        }

        audit(siteId, "APPLICATION_CREATED", "SiteApplication", appId.toString(), null, toJsonSafe(toApplicationView(app)));
        emit("impilo.indawo.site.application.created.v1", "SiteApplication", appId.toString(), siteId.toString(), Map.of(
                "application_id", appId,
                "site_id", siteId,
                "application_type", app.getApplicationType()
        ));
        return getSiteProfile(siteId);
    }

    @Transactional
    public SiteRegulatoryDtos.SiteRegulatoryProfileResponse submitApplication(UUID applicationId) {
        RequestContext ctx = RequestContextHolder.require();
        String actor = actorId();
        SiteApplicationEntity app = applicationRepository.findById(applicationId).orElseThrow(() -> new IllegalArgumentException("Application not found"));
        assertApplicantOrAdmin(app, actor);
        if (!"DRAFT".equals(app.getWorkflowState())) throw new IllegalStateException("Only DRAFT applications can be submitted");
        app.setWorkflowState("SUBMITTED");
        app.setSubmissionDate(OffsetDateTime.now());
        app.touch(actor);
        applicationRepository.save(app);

        SiteEntity site = siteRepository.findById(app.getSiteId()).orElseThrow();
        setSiteRegulatoryStatus(site, SiteRegulatoryStatus.UNDER_REVIEW.name(), "Application submitted");

        audit(site.getSiteId(), "APPLICATION_SUBMITTED", "SiteApplication", applicationId.toString(), null, toJsonSafe(toApplicationView(app)));
        emit("impilo.indawo.site.application.submitted.v1", "SiteApplication", applicationId.toString(), site.getSiteId().toString(), Map.of(
                "application_id", applicationId,
                "site_id", site.getSiteId()
        ));
        return getSiteProfile(site.getSiteId());
    }

    @Transactional
    public SiteRegulatoryDtos.InspectionView scheduleInspection(SiteRegulatoryDtos.ScheduleSiteInspectionRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        String actor = actorId();
        SiteEntity site = siteRepository.findById(request.siteId()).orElseThrow(() -> new IllegalArgumentException("Site not found"));

        UUID templateId = null;
        if (request.templateCode() != null && !request.templateCode().isBlank()) {
            templateId = templateRepository.findFirstByTenantIdAndCodeAndActiveFlagIsTrueOrderByVersionDesc(tenantId, request.templateCode())
                    .map(InspectionChecklistTemplateEntity::getTemplateId).orElse(null);
        }

        UUID inspectionId = UUID.randomUUID();
        SiteInspectionEntity inspection = new SiteInspectionEntity(inspectionId, tenantId, site.getSiteId(), request.inspectionType());
        inspection.setScheduledDate(request.scheduledDate());
        inspection.setApplicationId(request.applicationId());
        inspection.setTemplateId(templateId);
        if (request.inspectorRefs() != null && !request.inspectorRefs().isEmpty()) {
            inspection.setInspectorAssignmentsJson(toJsonSafe(Map.of("inspectors", request.inspectorRefs())));
        }
        inspection.touch(actor);
        inspectionRepository.save(inspection);

        setSiteRegulatoryStatus(site, SiteRegulatoryStatus.PENDING_INSPECTION.name(), "Inspection scheduled");
        audit(site.getSiteId(), "INSPECTION_SCHEDULED", "SiteInspection", inspectionId.toString(), null, toJsonSafe(toInspectionView(inspection)));
        emit("impilo.indawo.site.inspection.scheduled.v1", "SiteInspection", inspectionId.toString(), site.getSiteId().toString(), Map.of(
                "inspection_id", inspectionId,
                "site_id", site.getSiteId(),
                "inspection_type", inspection.getInspectionType(),
                "scheduled_date", inspection.getScheduledDate()
        ));

        return toInspectionView(inspection);
    }

    @Transactional
    public SiteRegulatoryDtos.InspectionView recordInspectionOutcome(UUID inspectionId, SiteRegulatoryDtos.RecordSiteInspectionOutcomeRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        String actor = actorId();
        SiteInspectionEntity inspection = inspectionRepository.findById(inspectionId).orElseThrow(() -> new IllegalArgumentException("Inspection not found"));
        SiteEntity site = siteRepository.findById(inspection.getSiteId()).orElseThrow();

        inspection.setActualDate(request.actualDate());
        inspection.setOutcome(request.outcome());
        inspection.setRecommendation(request.recommendation());
        inspection.setStatus("COMPLETED");
        inspection.setReportStatus("FINAL");
        if (request.nextInspectionDue() != null) inspection.setNextInspectionDue(request.nextInspectionDue());
        inspection.touch(actor);
        inspectionRepository.save(inspection);

        boolean hasCriticalFail = false;
        boolean hasAnyFail = false;
        int criticalFailCount = 0;
        int majorFailCount = 0;
        int minorFailCount = 0;
        BigDecimal scoreTotal = BigDecimal.ZERO;
        BigDecimal scoreMax = BigDecimal.ZERO;

        if (request.findings() != null) {
            for (SiteRegulatoryDtos.ChecklistFinding f : request.findings()) {
                UUID findingId = UUID.randomUUID();
                SiteInspectionFindingEntity finding = new SiteInspectionFindingEntity(
                        findingId,
                        UUID.fromString(ctx.tenantId()),
                        inspection.getInspectionId(),
                        f.status()
                );
                finding.setChecklistItemId(f.checklistItemId());
                finding.setSeverity(f.severity());
                finding.setComments(f.comments());
                if (f.evidenceRefs() != null && !f.evidenceRefs().isEmpty()) {
                    finding.setEvidenceRefsJson(toJsonSafe(f.evidenceRefs()));
                }
                finding.setRectificationDeadline(f.rectificationDeadline());
                findingRepository.save(finding);

                BigDecimal weight = weightForSeverity(f.severity());
                scoreMax = scoreMax.add(weight);
                if (!"FAIL".equalsIgnoreCase(f.status())) {
                    scoreTotal = scoreTotal.add(weight);
                }

                if ("FAIL".equalsIgnoreCase(f.status())) {
                    hasAnyFail = true;
                    if (f.checklistItemId() != null) {
                        itemRepository.findById(f.checklistItemId()).ifPresent(item -> {
                            if (item.isCriticalFlag()) {
                                // captured via outer mutable state by single-thread transactional usage
                            }
                        });
                        var item = itemRepository.findById(f.checklistItemId()).orElse(null);
                        if (item != null && item.isCriticalFlag()) {
                            hasCriticalFail = true;
                            criticalFailCount++;
                        }
                    }
                    if ("MAJOR".equalsIgnoreCase(f.severity())) majorFailCount++;
                    if ("MINOR".equalsIgnoreCase(f.severity())) minorFailCount++;
                    if (f.createComplianceAction()) {
                        SiteComplianceActionEntity action = new SiteComplianceActionEntity(UUID.randomUUID(),
                                UUID.fromString(ctx.tenantId()),
                                site.getSiteId(),
                                "RECTIFY_FINDING");
                        action.setFindingId(findingId);
                        action.setDueDate(f.rectificationDeadline());
                        action.setOwnerRef(site.getOperatorName());
                        actionRepository.save(action);
                        emit("impilo.indawo.site.compliance_action.created.v1", "SiteComplianceAction", action.getActionId().toString(), site.getSiteId().toString(), Map.of(
                                "action_id", action.getActionId(),
                                "site_id", site.getSiteId(),
                                "finding_id", findingId
                        ));
                    }
                }
            }
        }

        inspection.setCriticalFailCount(criticalFailCount);
        inspection.setMajorFailCount(majorFailCount);
        inspection.setMinorFailCount(minorFailCount);
        inspection.setScoreTotal(scoreTotal);
        inspection.setScoreMax(scoreMax);
        if (scoreMax.compareTo(BigDecimal.ZERO) > 0) {
            inspection.setScorePercent(scoreTotal.multiply(BigDecimal.valueOf(100)).divide(scoreMax, 2, java.math.RoundingMode.HALF_UP));
        }
        inspectionRepository.save(inspection);

        if (hasCriticalFail) {
            setSiteRegulatoryStatus(site, SiteRegulatoryStatus.PENDING_RECTIFICATION.name(), "Critical findings require rectification");
        } else if (hasAnyFail) {
            setSiteRegulatoryStatus(site, SiteRegulatoryStatus.PENDING_RECTIFICATION.name(), "Findings require rectification");
        } else {
            setSiteRegulatoryStatus(site, SiteRegulatoryStatus.PENDING_DECISION.name(), "Inspection passed; pending decision");
        }

        audit(site.getSiteId(), "INSPECTION_RECORDED", "SiteInspection", inspectionId.toString(), null, toJsonSafe(toInspectionView(inspection)));
        emit("impilo.indawo.site.inspection.completed.v1", "SiteInspection", inspectionId.toString(), site.getSiteId().toString(), Map.of(
                "inspection_id", inspectionId,
                "site_id", site.getSiteId(),
                "outcome", inspection.getOutcome(),
                "has_any_fail", hasAnyFail,
                "has_critical_fail", hasCriticalFail
        ));

        return toInspectionView(inspection);
    }

    @Transactional
    public SiteRegulatoryDtos.SiteRegulatoryProfileResponse updateComplianceAction(UUID actionId, SiteRegulatoryDtos.UpdateComplianceActionRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        String actor = actorId();
        SiteComplianceActionEntity action = actionRepository.findById(actionId).orElseThrow(() -> new IllegalArgumentException("Action not found"));
        action.setStatus(request.status());
        action.setCompletionNotes(request.completionNotes());
        if ("VERIFIED".equalsIgnoreCase(request.status())) {
            action.verify(actor, request.completionNotes());
        }
        actionRepository.save(action);

        audit(action.getSiteId(), "COMPLIANCE_ACTION_UPDATED", "SiteComplianceAction", actionId.toString(), null, toJsonSafe(toComplianceView(action)));
        return getSiteProfile(action.getSiteId());
    }

    @Transactional
    public SiteRegulatoryDtos.LicenceView issueLicence(SiteRegulatoryDtos.IssueLicenceRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        String actor = actorId();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        SiteEntity site = siteRepository.findById(request.siteId()).orElseThrow(() -> new IllegalArgumentException("Site not found"));

        SiteLicenceEntity licence = new SiteLicenceEntity(UUID.randomUUID(), tenantId, site.getSiteId(),
                request.licenceType(), request.issueDate(), request.expiryDate());
        licence.setRestrictionSummary(request.restrictionSummary());
        licence.setIssuedUnderAuthority(actor);
        licenceRepository.save(licence);

        setSiteRegulatoryStatus(site, SiteRegulatoryStatus.LICENSED_ACTIVE.name(), "Licence issued");
        site.setLifecycleStatus(SiteLifecycleStatus.ACTIVE.name());
        siteRepository.save(site);

        audit(site.getSiteId(), "LICENCE_ISSUED", "SiteLicence", licence.getLicenceId().toString(), null, toJsonSafe(toLicenceView(licence)));
        emit("impilo.indawo.site.licence.issued.v1", "SiteLicence", licence.getLicenceId().toString(), site.getSiteId().toString(), Map.of(
                "licence_id", licence.getLicenceId(),
                "site_id", site.getSiteId(),
                "issue_date", licence.getIssueDate(),
                "expiry_date", licence.getExpiryDate()
        ));

        return toLicenceView(licence);
    }

    @Transactional
    public SiteRegulatoryDtos.SiteRegulatoryProfileResponse createRenewalApplication(UUID siteId, String applicantName) {
        RequestContext ctx = RequestContextHolder.require();
        String actor = actorId();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        SiteEntity site = siteRepository.findById(siteId).orElseThrow(() -> new IllegalArgumentException("Site not found"));

        SiteApplicationEntity app = new SiteApplicationEntity(UUID.randomUUID(), tenantId, "RENEWAL", applicantName);
        app.setSiteId(siteId);
        app.setCreatedBy(actor);
        app.setUpdatedBy(actor);
        applicationRepository.save(app);

        setSiteRegulatoryStatus(site, SiteRegulatoryStatus.RENEWAL_IN_PROGRESS.name(), "Renewal started");
        audit(siteId, "RENEWAL_APPLICATION_CREATED", "SiteApplication", app.getApplicationId().toString(), null, toJsonSafe(toApplicationView(app)));
        emit("impilo.indawo.site.licence.renewal.started.v1", "SiteApplication", app.getApplicationId().toString(), siteId.toString(), Map.of(
                "application_id", app.getApplicationId(),
                "site_id", siteId
        ));
        return getSiteProfile(siteId);
    }

    @Transactional
    public SiteRegulatoryDtos.EnforcementCaseView openEnforcementCase(SiteRegulatoryDtos.OpenEnforcementCaseRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        SiteEntity site = siteRepository.findById(request.siteId()).orElseThrow(() -> new IllegalArgumentException("Site not found"));

        SiteEnforcementCaseEntity caze = new SiteEnforcementCaseEntity(UUID.randomUUID(), tenantId, site.getSiteId(), request.triggerType());
        caze.setRecommendation(request.recommendation());
        caze.setAuthorityRoute(request.authorityRoute());
        if (request.linkedRefs() != null && !request.linkedRefs().isEmpty()) {
            caze.setLinkedRefsJson(toJsonSafe(request.linkedRefs()));
        }
        caseRepository.save(caze);

        audit(site.getSiteId(), "ENFORCEMENT_CASE_OPENED", "SiteEnforcementCase", caze.getCaseId().toString(), null, toJsonSafe(toCaseView(caze)));
        emit("impilo.indawo.site.enforcement_case.created.v1", "SiteEnforcementCase", caze.getCaseId().toString(), site.getSiteId().toString(), Map.of(
                "case_id", caze.getCaseId(),
                "site_id", site.getSiteId(),
                "trigger_type", caze.getTriggerType()
        ));
        return toCaseView(caze);
    }

    // ---------------------------------------------------------------------
    // Documents
    // ---------------------------------------------------------------------

    @Transactional
    public SiteRegulatoryDtos.SiteRegulatoryProfileResponse uploadDocument(
            MultipartFile file,
            UUID siteId,
            UUID applicationId,
            String documentType,
            String notes
    ) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        String actor = actorId();

        SiteEntity site = siteRepository.findById(siteId).orElseThrow(() -> new IllegalArgumentException("Site not found"));
        if (applicationId != null) {
            SiteApplicationEntity app = applicationRepository.findById(applicationId).orElseThrow(() -> new IllegalArgumentException("Application not found"));
            assertApplicantOrAdmin(app, actor);
        }

        String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        UUID objectId = documentStoreClient.upload(file, mimeType, Map.of(
                "site_id", siteId.toString(),
                "document_type", documentType
        ));

        SiteDocumentEntity doc = new SiteDocumentEntity(UUID.randomUUID(), tenantId, siteId, documentType, "docstore:" + objectId);
        doc.setApplicationId(applicationId);
        doc.setUploadedBy(actor);
        doc.setNotes(notes);
        documentRepository.save(doc);

        audit(siteId, "DOCUMENT_UPLOADED", "SiteDocument", doc.getDocumentId().toString(), null, toJsonSafe(toDocumentView(doc)));
        emit("impilo.indawo.site.document.uploaded.v1", "SiteDocument", doc.getDocumentId().toString(), siteId.toString(), Map.of(
                "document_id", doc.getDocumentId(),
                "site_id", siteId,
                "application_id", applicationId,
                "document_type", documentType,
                "object_id", objectId
        ));

        // If an application is in SUBMITTED and documents are added, nudge workflow state.
        if (applicationId != null) {
            applicationRepository.findById(applicationId).ifPresent(app -> {
                if ("SUBMITTED".equals(app.getWorkflowState())) {
                    app.setWorkflowState("UNDER_ADMIN_REVIEW");
                    app.touch(actor);
                    applicationRepository.save(app);
                }
            });
        }

        return getSiteProfile(site.getSiteId());
    }

    @Transactional
    public SiteRegulatoryDtos.SiteRegulatoryProfileResponse verifyDocument(UUID documentId, SiteRegulatoryDtos.VerifyDocumentRequest request) {
        String actor = actorId();
        SiteDocumentEntity doc = documentRepository.findById(documentId).orElseThrow(() -> new IllegalArgumentException("Document not found"));
        doc.review(actor, request.verificationState(), request.notes());
        documentRepository.save(doc);

        audit(doc.getSiteId(), "DOCUMENT_VERIFIED", "SiteDocument", documentId.toString(), null, toJsonSafe(toDocumentView(doc)));
        emit("impilo.indawo.site.document.verified.v1", "SiteDocument", documentId.toString(), doc.getSiteId().toString(), Map.of(
                "document_id", documentId,
                "site_id", doc.getSiteId(),
                "verification_state", doc.getVerificationState()
        ));
        return getSiteProfile(doc.getSiteId());
    }

    // ---------------------------------------------------------------------
    // Assignments
    // ---------------------------------------------------------------------

    @Transactional
    public SiteRegulatoryDtos.AssignmentView createAssignment(SiteRegulatoryDtos.CreateAssignmentRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        String actor = actorId();
        SiteEntity site = siteRepository.findById(request.siteId()).orElseThrow(() -> new IllegalArgumentException("Site not found"));

        SiteAssignmentEntity a = new SiteAssignmentEntity(UUID.randomUUID(), tenantId, site.getSiteId(), request.assignmentType());
        a.setAssignedRole(request.assignedRole());
        a.setAssignedToRef(request.assignedToRef());
        a.setJurisdictionRef(request.jurisdictionRef() != null ? request.jurisdictionRef() : site.getJurisdictionRef());
        if (request.priority() != null) a.setPriority(request.priority());
        a.setDueDate(request.dueDate());
        a.setNotes(request.notes());
        a.setCreatedBy(actor);
        assignmentRepository.save(a);

        audit(site.getSiteId(), "ASSIGNMENT_CREATED", "SiteAssignment", a.getAssignmentId().toString(), null, toJsonSafe(toAssignmentView(a)));
        emit("impilo.indawo.site.assignment.created.v1", "SiteAssignment", a.getAssignmentId().toString(), site.getSiteId().toString(), Map.of(
                "assignment_id", a.getAssignmentId(),
                "site_id", site.getSiteId(),
                "assignment_type", a.getAssignmentType(),
                "assigned_to", a.getAssignedToRef()
        ));
        return toAssignmentView(a);
    }

    @Transactional
    public SiteRegulatoryDtos.AssignmentView updateAssignment(UUID assignmentId, SiteRegulatoryDtos.UpdateAssignmentRequest request) {
        SiteAssignmentEntity a = assignmentRepository.findById(assignmentId).orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        if (request.status() != null) a.setStatus(request.status());
        if (request.notes() != null) a.setNotes(request.notes());
        assignmentRepository.save(a);

        audit(a.getSiteId(), "ASSIGNMENT_UPDATED", "SiteAssignment", assignmentId.toString(), null, toJsonSafe(toAssignmentView(a)));
        return toAssignmentView(a);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listChecklistTemplates(String inspectionType, String siteCategory) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        return templateRepository.listApplicable(tenantId, InspectionChecklistTemplateRepository.SYSTEM_TENANT, inspectionType, siteCategory)
                .stream().map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("template_id", t.getTemplateId());
                    m.put("code", t.getCode());
                    m.put("version", t.getVersion());
                    m.put("name", t.getName());
                    m.put("applicable_site_category", t.getApplicableSiteCategory());
                    m.put("applicable_inspection_type", t.getApplicableInspectionType());
                    return m;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getChecklistTemplate(UUID templateId) {
        RequestContextHolder.require();
        InspectionChecklistTemplateEntity template = templateRepository.findById(templateId).orElseThrow(() -> new IllegalArgumentException("Template not found"));
        List<InspectionChecklistItemEntity> items = itemRepository.findByTemplateIdOrderByDisplayOrderAsc(templateId);
        return Map.of(
                "template", Map.of(
                        "template_id", template.getTemplateId(),
                        "code", template.getCode(),
                        "version", template.getVersion(),
                        "name", template.getName(),
                        "applicable_site_category", template.getApplicableSiteCategory(),
                        "applicable_inspection_type", template.getApplicableInspectionType()
                ),
                "items", items.stream().map(i -> Map.of(
                        "item_id", i.getItemId(),
                        "section", i.getSection(),
                        "code", i.getCode(),
                        "requirement_text", i.getRequirementText(),
                        "severity", i.getSeverity(),
                        "evidence_type", i.getEvidenceType(),
                        "pass_criteria", i.getPassCriteria(),
                        "critical_flag", i.isCriticalFlag(),
                        "display_order", i.getDisplayOrder()
                )).toList()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardSummary() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        long total = siteRepository.searchSites(tenantId, null, null, null, null, null, PageRequest.of(0, 1)).getTotalElements();
        var expiring = licenceRepository.findActiveExpiringBy(tenantId, LocalDate.now().plusDays(60));
        long openActions = actionRepository.countByTenantIdAndStatus(tenantId, "OPEN");
        long openAssignments = assignmentRepository.countByTenantIdAndStatus(tenantId, "OPEN");
        return Map.of(
                "total_sites", total,
                "expiring_licences_next_60_days", expiring.size(),
                "open_compliance_actions", openActions,
                "open_assignments", openAssignments
        );
    }

    @Transactional(readOnly = true)
    public List<SiteRegulatoryDtos.InspectionRegisterRow> listInspectionRegister(String status, int size) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        var page = PageRequest.of(0, Math.max(1, Math.min(size, 200)));
        List<SiteInspectionEntity> inspections = normalize(status) == null
                ? inspectionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, page)
                : inspectionRepository.findByTenantIdAndStatusIgnoreCaseOrderByCreatedAtDesc(tenantId, status, page);
        Map<UUID, SiteEntity> sitesById = hydrateSites(inspections.stream().map(SiteInspectionEntity::getSiteId).toList());
        return inspections.stream().map(i -> {
            SiteEntity site = sitesById.get(i.getSiteId());
            return new SiteRegulatoryDtos.InspectionRegisterRow(
                    i.getInspectionId(),
                    i.getSiteId(),
                    site != null ? site.getName() : "Unknown site",
                    site != null ? site.getType() : "Unknown",
                    i.getInspectionType(),
                    i.getScheduledDate(),
                    i.getActualDate(),
                    i.getStatus(),
                    i.getOutcome(),
                    i.getScorePercent(),
                    i.getCriticalFailCount());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<SiteRegulatoryDtos.ComplianceActionRow> listComplianceActions(String status, int size) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        var page = PageRequest.of(0, Math.max(1, Math.min(size, 200)));
        List<SiteComplianceActionEntity> actions = normalize(status) == null
                ? actionRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId, page)
                : actionRepository.findByTenantIdAndStatusIgnoreCaseOrderByUpdatedAtDesc(tenantId, status, page);
        Map<UUID, SiteEntity> sitesById = hydrateSites(actions.stream().map(SiteComplianceActionEntity::getSiteId).toList());
        return actions.stream().map(a -> {
            SiteEntity site = sitesById.get(a.getSiteId());
            return new SiteRegulatoryDtos.ComplianceActionRow(
                    a.getActionId(),
                    a.getSiteId(),
                    site != null ? site.getName() : "Unknown site",
                    site != null ? site.getType() : "Unknown",
                    a.getActionType(),
                    a.getOwnerRef(),
                    a.getDueDate(),
                    a.getStatus());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<SiteRegulatoryDtos.EnforcementCaseRow> listEnforcementCases(String status, int size) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        var page = PageRequest.of(0, Math.max(1, Math.min(size, 200)));
        List<SiteEnforcementCaseEntity> cases = normalize(status) == null
                ? caseRepository.findByTenantIdOrderByOpenedAtDesc(tenantId, page)
                : caseRepository.findByTenantIdAndStatusIgnoreCaseOrderByOpenedAtDesc(tenantId, status, page);
        Map<UUID, SiteEntity> sitesById = hydrateSites(cases.stream().map(SiteEnforcementCaseEntity::getSiteId).toList());
        return cases.stream().map(c -> {
            SiteEntity site = sitesById.get(c.getSiteId());
            return new SiteRegulatoryDtos.EnforcementCaseRow(
                    c.getCaseId(),
                    c.getSiteId(),
                    site != null ? site.getName() : "Unknown site",
                    site != null ? site.getType() : "Unknown",
                    c.getTriggerType(),
                    c.getStatus(),
                    c.getRecommendation(),
                    c.getAuthorityRoute());
        }).toList();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void applySiteDraft(SiteEntity site, SiteRegulatoryDtos.SiteDraft d) {
        if (d.siteCode() != null) site.setSiteCode(d.siteCode());
        if (d.siteCategory() != null) site.setSiteCategory(d.siteCategory());
        if (d.riskClass() != null) site.setRiskClass(d.riskClass());
        if (d.province() != null) site.setProvince(d.province());
        if (d.district() != null) site.setDistrict(d.district());
        if (d.ward() != null) site.setWard(d.ward());
    }

    private void setSiteRegulatoryStatus(SiteEntity site, String newStatus, String reason) {
        RequestContext ctx = RequestContextHolder.require();
        String actor = actorId();
        String previous = site.getRegulatoryStatus();
        if (Objects.equals(previous, newStatus)) return;
        site.setRegulatoryStatus(newStatus);
        site.setUpdatedAt(OffsetDateTime.now());
        siteRepository.save(site);
        statusHistoryRepository.save(new SiteStatusHistoryEntity(UUID.randomUUID(), UUID.fromString(ctx.tenantId()),
                site.getSiteId(), previous, newStatus, reason, actor));
        emit("impilo.indawo.site.status.changed.v1", "Site", site.getSiteId().toString(), site.getSiteId().toString(), Map.of(
                "site_id", site.getSiteId(),
                "previous_status", previous,
                "new_status", newStatus,
                "reason", reason
        ));
    }

    private void audit(UUID siteId, String action, String targetType, String targetId, String beforeJson, String afterJson) {
        RequestContext ctx = RequestContextHolder.require();
        String actor = actorId();
        auditRepository.save(new AuditEventEntity(
                UUID.randomUUID(),
                UUID.fromString(ctx.tenantId()),
                siteId,
                actor,
                "USER",
                action,
                targetType,
                targetId,
                ctx.correlationId() != null ? UUID.fromString(ctx.correlationId()) : null
        ));
    }

    private void emit(String eventType, String aggregateType, String aggregateId, String partitionKey, Map<String, Object> payload) {
        RequestContext ctx = RequestContextHolder.require();
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setSchemaVersion("1");
        outbox.setCorrelationId(ctx.correlationId() != null ? UUID.fromString(ctx.correlationId()) : null);
        outbox.setCausationId(ctx.correlationId() != null ? UUID.fromString(ctx.correlationId()) : null);
        outbox.setIdempotencyKey(UUID.randomUUID().toString());
        outbox.setProducer(PRODUCER);
        outbox.setTenantId(UUID.fromString(ctx.tenantId()));
        outbox.setPodId(ctx.podId() != null ? ctx.podId() : "national-spine");
        outbox.setSubjectId(partitionKey);
        outbox.setSubjectType(aggregateType);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJsonSafe(payload));
        outbox.setPartitionKey(partitionKey);
        outboxRepository.save(outbox);
    }

    private String actorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        RequestContext ctx = RequestContextHolder.get();
        if (ctx != null && ctx.principal() != null && ctx.principal().getName() != null && !ctx.principal().getName().isBlank()) {
            return ctx.principal().getName();
        }
        return "anonymous";
    }

    private void assertApplicantOrAdmin(SiteApplicationEntity app, String actor) {
        // Best-effort: enforce "creator can submit/update" while still allowing privileged roles via JWT.
        // Role enforcement is primarily handled at controller via @PreAuthorize.
        if (app.getCreatedBy() != null && !app.getCreatedBy().isBlank() && !Objects.equals(app.getCreatedBy(), actor)) {
            // Allow if the caller is privileged.
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getAuthorities() != null) {
                boolean privileged = auth.getAuthorities().stream().anyMatch(a ->
                        a.getAuthority().equals("ROLE_SYSTEM_ADMIN") ||
                        a.getAuthority().equals("ROLE_REGISTRY_ADMIN") ||
                        a.getAuthority().equals("ROLE_DEVELOPER"));
                if (privileged) return;
            }
            throw new IllegalStateException("Only the applicant/creator or registry admin may perform this action");
        }
    }

    private BigDecimal weightForSeverity(String severity) {
        if (severity == null) return BigDecimal.ONE;
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> BigDecimal.valueOf(5);
            case "MAJOR" -> BigDecimal.valueOf(3);
            case "MINOR" -> BigDecimal.ONE;
            default -> BigDecimal.ONE;
        };
    }

    private String toJsonSafe(Object v) {
        if (v == null) return null;
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            log.warn("JSON serialization failed: {}", e.getMessage());
            return null;
        }
    }

    private Object parseJsonSafe(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Map<UUID, SiteEntity> hydrateSites(List<UUID> siteIds) {
        Map<UUID, SiteEntity> sitesById = new HashMap<>();
        for (UUID siteId : siteIds) {
            if (siteId == null || sitesById.containsKey(siteId)) {
                continue;
            }
            siteRepository.findById(siteId).ifPresent(site -> sitesById.put(siteId, site));
        }
        return sitesById;
    }

    private SiteRegulatoryDtos.SiteView toSiteView(SiteEntity s) {
        return new SiteRegulatoryDtos.SiteView(
                s.getSiteId(),
                s.getSiteCode(),
                s.getName(),
                s.getType(),
                s.getSiteCategory(),
                s.getRiskClass(),
                s.getProvince(),
                s.getDistrict(),
                s.getWard(),
                s.getRegulatoryStatus(),
                s.getLifecycleStatus(),
                s.isActiveFlag()
        );
    }

    private SiteRegulatoryDtos.ApplicationView toApplicationView(SiteApplicationEntity a) {
        return new SiteRegulatoryDtos.ApplicationView(
                a.getApplicationId(),
                a.getSiteId(),
                a.getApplicationType(),
                a.getWorkflowState(),
                a.getPriority(),
                a.getFeeState(),
                a.getFinalDecision(),
                a.getApplicantName(),
                a.getNotes()
        );
    }

    private SiteRegulatoryDtos.OperatorView toOperatorView(SiteOperatorEntity o) {
        return new SiteRegulatoryDtos.OperatorView(
                o.getOperatorId(),
                o.getSiteId(),
                o.getOperatorType(),
                o.getOperatorName(),
                o.getContactPhone(),
                o.getContactEmail(),
                o.isActiveFlag()
        );
    }

    private SiteRegulatoryDtos.InspectionView toInspectionView(SiteInspectionEntity i) {
        return new SiteRegulatoryDtos.InspectionView(
                i.getInspectionId(),
                i.getSiteId(),
                i.getApplicationId(),
                i.getInspectionType(),
                i.getScheduledDate(),
                i.getActualDate(),
                i.getStatus(),
                i.getReportStatus(),
                i.getOutcome(),
                i.getSeveritySummary(),
                i.getTemplateId(),
                i.getScoreTotal(),
                i.getScoreMax(),
                i.getScorePercent(),
                i.getCriticalFailCount(),
                i.getMajorFailCount(),
                i.getMinorFailCount()
        );
    }

    private SiteRegulatoryDtos.FindingView toFindingView(SiteInspectionFindingEntity f) {
        return new SiteRegulatoryDtos.FindingView(
                f.getFindingId(),
                f.getInspectionId(),
                f.getChecklistItemId(),
                f.getStatus(),
                f.getSeverity(),
                f.getComments(),
                parseJsonSafe(f.getEvidenceRefsJson()),
                f.getRectificationDeadline(),
                f.getRectificationStatus()
        );
    }

    private SiteRegulatoryDtos.DocumentView toDocumentView(SiteDocumentEntity d) {
        return new SiteRegulatoryDtos.DocumentView(
                d.getDocumentId(),
                d.getSiteId(),
                d.getApplicationId(),
                d.getDocumentType(),
                d.getFileReference(),
                d.getVerificationState(),
                d.getUploadedBy(),
                d.getUploadedAt() != null ? d.getUploadedAt().toString() : null,
                d.getReviewedBy(),
                d.getReviewedAt() != null ? d.getReviewedAt().toString() : null,
                d.getNotes()
        );
    }

    private SiteRegulatoryDtos.AssignmentView toAssignmentView(SiteAssignmentEntity a) {
        return new SiteRegulatoryDtos.AssignmentView(
                a.getAssignmentId(),
                a.getSiteId(),
                a.getAssignmentType(),
                a.getAssignedRole(),
                a.getAssignedToRef(),
                a.getJurisdictionRef(),
                a.getStatus(),
                a.getPriority(),
                a.getDueDate(),
                a.getNotes()
        );
    }

    private SiteRegulatoryDtos.ComplianceActionView toComplianceView(SiteComplianceActionEntity a) {
        return new SiteRegulatoryDtos.ComplianceActionView(
                a.getActionId(),
                a.getSiteId(),
                a.getFindingId(),
                a.getActionType(),
                a.getOwnerRef(),
                a.getDueDate(),
                a.getStatus(),
                a.getCompletionNotes()
        );
    }

    private SiteRegulatoryDtos.LicenceView toLicenceView(SiteLicenceEntity l) {
        return new SiteRegulatoryDtos.LicenceView(
                l.getLicenceId(),
                l.getSiteId(),
                l.getLicenceType(),
                l.getIssueDate(),
                l.getExpiryDate(),
                l.getStatus(),
                l.getRestrictionSummary()
        );
    }

    private SiteRegulatoryDtos.EnforcementCaseView toCaseView(SiteEnforcementCaseEntity c) {
        return new SiteRegulatoryDtos.EnforcementCaseView(
                c.getCaseId(),
                c.getSiteId(),
                c.getTriggerType(),
                c.getStatus(),
                c.getRecommendation(),
                c.getAuthorityRoute()
        );
    }

    private SiteRegulatoryDtos.StatusHistoryView toStatusHistoryView(SiteStatusHistoryEntity h) {
        return new SiteRegulatoryDtos.StatusHistoryView(
                h.getId(),
                h.getPreviousStatus(),
                h.getNewStatus(),
                h.getReason(),
                h.getChangedBy()
        );
    }

    private SiteRegulatoryDtos.AuditEventView toAuditView(AuditEventEntity a) {
        return new SiteRegulatoryDtos.AuditEventView(
                a.getAuditId(),
                a.getActorRef(),
                a.getActorType(),
                a.getAction(),
                a.getTargetType(),
                a.getTargetId(),
                a.getNotes()
        );
    }
}

