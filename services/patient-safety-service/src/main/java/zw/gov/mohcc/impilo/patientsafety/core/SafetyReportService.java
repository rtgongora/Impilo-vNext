package zw.gov.mohcc.impilo.patientsafety.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.patientsafety.api.dto.AddAttachmentRequest;
import zw.gov.mohcc.impilo.patientsafety.api.dto.AddEventRequest;
import zw.gov.mohcc.impilo.patientsafety.api.dto.AddProductRequest;
import zw.gov.mohcc.impilo.patientsafety.api.dto.CreateReportRequest;
import zw.gov.mohcc.impilo.patientsafety.api.dto.ReportResponse;
import zw.gov.mohcc.impilo.patientsafety.api.dto.ReportSummary;
import zw.gov.mohcc.impilo.patientsafety.api.dto.UpdateReportRequest;
import zw.gov.mohcc.impilo.patientsafety.core.PatientSafetyExceptions.ConflictException;
import zw.gov.mohcc.impilo.patientsafety.core.PatientSafetyExceptions.NotFoundException;
import zw.gov.mohcc.impilo.patientsafety.domain.AttachmentRefEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.ProductExposureEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.SafetyCaseEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.SafetyEventEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.SafetyReportEntity;
import zw.gov.mohcc.impilo.patientsafety.repository.AttachmentRefRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.ProductExposureRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.SafetyCaseRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.SafetyEventRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.SafetyReportRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lifecycle for safety reports (the captured ADR/AEFI intake). On submit a report is assigned a
 * reference code, a regulatory {@link SafetyCaseEntity} is opened, and domain events are emitted
 * (and an additional serious-signal event for surveillance when the report is serious).
 */
@Service
public class SafetyReportService {

    private static final Set<String> EDITABLE = Set.of("DRAFT", "NEEDS_MORE_INFORMATION");

    private final SafetyReportRepository reportRepository;
    private final ProductExposureRepository productRepository;
    private final SafetyEventRepository eventRepository;
    private final AttachmentRefRepository attachmentRepository;
    private final SafetyCaseRepository caseRepository;
    private final SafetyCaseService caseService;
    private final SafetyEventService events;

    public SafetyReportService(SafetyReportRepository reportRepository,
                               ProductExposureRepository productRepository,
                               SafetyEventRepository eventRepository,
                               AttachmentRefRepository attachmentRepository,
                               SafetyCaseRepository caseRepository,
                               SafetyCaseService caseService,
                               SafetyEventService events) {
        this.reportRepository = reportRepository;
        this.productRepository = productRepository;
        this.eventRepository = eventRepository;
        this.attachmentRepository = attachmentRepository;
        this.caseRepository = caseRepository;
        this.caseService = caseService;
        this.events = events;
    }

    @Transactional
    public ReportResponse createDraft(UUID tenantId, String podId, String actorId, String actorType,
                                      CreateReportRequest req) {
        SafetyReportEntity r = new SafetyReportEntity(tenantId, podId, req.reportType(), req.reporterKind());
        r.setReporterActorId(actorId);
        r.setReporterActorType(actorType);
        r.setCreatedBy(actorId);
        if (req.sourceContext() != null && !req.sourceContext().isBlank()) {
            r.setSourceContext(req.sourceContext());
        }
        applyReporterAndSubject(r, req);
        reportRepository.save(r);

        if (req.products() != null) {
            for (AddProductRequest p : req.products()) {
                productRepository.save(toProduct(r, p));
            }
        }
        if (req.events() != null) {
            for (AddEventRequest e : req.events()) {
                eventRepository.save(toEvent(r, e));
            }
        }
        return assemble(r);
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(UUID tenantId, UUID id) {
        return assemble(requireReport(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<ReportSummary> listReports(UUID tenantId, String status, String reporterActorId, String facilityId) {
        List<SafetyReportEntity> reports;
        if (status != null && !status.isBlank()) {
            reports = reportRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        } else if (reporterActorId != null && !reporterActorId.isBlank()) {
            reports = reportRepository.findByTenantIdAndReporterActorIdOrderByCreatedAtDesc(tenantId, reporterActorId);
        } else if (facilityId != null && !facilityId.isBlank()) {
            reports = reportRepository.findByTenantIdAndReporterFacilityIdOrderByCreatedAtDesc(tenantId, facilityId);
        } else {
            reports = reportRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        return reports.stream().map(this::summary).toList();
    }

    @Transactional
    public ReportResponse updateDraft(UUID tenantId, UUID id, UpdateReportRequest req) {
        SafetyReportEntity r = requireReport(tenantId, id);
        requireEditable(r);
        if (req.reporterName() != null) r.setReporterName(req.reporterName());
        if (req.reporterRole() != null) r.setReporterRole(req.reporterRole());
        if (req.reporterFacilityId() != null) r.setReporterFacilityId(req.reporterFacilityId());
        if (req.reporterPhone() != null) r.setReporterPhone(req.reporterPhone());
        if (req.reporterEmail() != null) r.setReporterEmail(req.reporterEmail());
        if (req.subjectCpid() != null) r.setSubjectCpid(req.subjectCpid());
        if (req.subjectIsSelf() != null) r.setSubjectIsSelf(req.subjectIsSelf());
        if (req.subjectInitials() != null) r.setSubjectInitials(req.subjectInitials());
        if (req.subjectSex() != null) r.setSubjectSex(req.subjectSex());
        if (req.subjectDob() != null) r.setSubjectDob(req.subjectDob());
        if (req.subjectAgeValue() != null) r.setSubjectAgeValue(req.subjectAgeValue());
        if (req.subjectAgeUnit() != null) r.setSubjectAgeUnit(req.subjectAgeUnit());
        if (req.subjectWeightKg() != null) r.setSubjectWeightKg(req.subjectWeightKg());
        if (req.encounterId() != null) r.setEncounterId(req.encounterId());
        if (req.narrative() != null) r.setNarrative(req.narrative());
        if (req.onsetDate() != null) r.setOnsetDate(req.onsetDate());
        if (req.serious() != null) r.setSerious(req.serious());
        if (req.seriousnessReasons() != null) r.setSeriousnessReasons(JsonUtil.write(req.seriousnessReasons()));
        if (req.seriousnessOtherText() != null) r.setSeriousnessOtherText(req.seriousnessOtherText());
        if (req.formPackKey() != null) r.setFormPackKey(req.formPackKey());
        if (req.formPackVersion() != null) r.setFormPackVersion(req.formPackVersion());
        if (req.formData() != null) r.setFormData(JsonUtil.write(req.formData()));
        r.touch();
        reportRepository.save(r);
        return assemble(r);
    }

    @Transactional
    public ReportResponse addProduct(UUID tenantId, UUID id, AddProductRequest req) {
        SafetyReportEntity r = requireReport(tenantId, id);
        requireEditable(r);
        productRepository.save(toProduct(r, req));
        r.touch();
        reportRepository.save(r);
        return assemble(r);
    }

    @Transactional
    public ReportResponse addEvent(UUID tenantId, UUID id, AddEventRequest req) {
        SafetyReportEntity r = requireReport(tenantId, id);
        requireEditable(r);
        eventRepository.save(toEvent(r, req));
        r.touch();
        reportRepository.save(r);
        return assemble(r);
    }

    @Transactional
    public ReportResponse addAttachment(UUID tenantId, UUID id, AddAttachmentRequest req) {
        SafetyReportEntity r = requireReport(tenantId, id);
        AttachmentRefEntity a = new AttachmentRefEntity(r.getId(), r.getTenantId(), req.documentObjectId());
        a.setFilename(req.filename());
        a.setMimeType(req.mimeType());
        a.setDescription(req.description());
        a.setConsentObtained(Boolean.TRUE.equals(req.consentObtained()));
        attachmentRepository.save(a);
        return assemble(r);
    }

    @Transactional
    public ReportResponse submit(UUID tenantId, String podId, String actorId, UUID correlationId, UUID id) {
        SafetyReportEntity r = requireReport(tenantId, id);
        if (!EDITABLE.contains(r.getStatus())) {
            throw new ConflictException("Report cannot be submitted from status " + r.getStatus());
        }
        List<ProductExposureEntity> products = productRepository.findByReportIdOrderByCreatedAtAsc(r.getId());
        List<SafetyEventEntity> reactions = eventRepository.findByReportIdOrderByCreatedAtAsc(r.getId());
        if (products.isEmpty()) {
            throw new ConflictException("At least one implicated product (medicine/vaccine) is required to submit");
        }
        if (reactions.isEmpty() && (r.getNarrative() == null || r.getNarrative().isBlank())) {
            throw new ConflictException("Describe at least one reaction or provide a narrative before submitting");
        }

        if (r.getReferenceCode() == null) {
            r.setReferenceCode(nextReportReference(tenantId));
        }
        if (r.getReportDate() == null) {
            r.setReportDate(LocalDate.now());
        }
        r.setStatus("SUBMITTED");
        r.setSubmittedAt(OffsetDateTime.now());
        r.touch();
        reportRepository.save(r);

        SafetyCaseEntity safetyCase = caseService.openCaseForReport(r, correlationId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("report_reference", r.getReferenceCode());
        payload.put("report_type", r.getReportType());
        payload.put("reporter_kind", r.getReporterKind());
        payload.put("is_serious", r.isSerious());
        payload.put("case_id", safetyCase.getId().toString());
        payload.put("case_reference", safetyCase.getCaseReference());
        payload.put("product_count", products.size());
        payload.put("reaction_count", reactions.size());
        String subjectId = r.getSubjectCpid() != null && !r.getSubjectCpid().isBlank()
                ? r.getSubjectCpid() : r.getId().toString();
        events.emitReportSubmitted(r.getId(), correlationId, tenantId, r.getPodId(), subjectId, payload);
        // Serious reports also feed the surveillance signal/cluster stream.
        if (r.isSerious()) {
            events.emitReportSerious(r.getId(), correlationId, tenantId, r.getPodId(), subjectId, payload);
        }
        return assemble(r);
    }

    // ---- helpers ------------------------------------------------------------

    private SafetyReportEntity requireReport(UUID tenantId, UUID id) {
        return reportRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Safety report not found"));
    }

    private void requireEditable(SafetyReportEntity r) {
        if (!EDITABLE.contains(r.getStatus())) {
            throw new ConflictException("Report is not editable in status " + r.getStatus());
        }
    }

    private void applyReporterAndSubject(SafetyReportEntity r, CreateReportRequest req) {
        r.setReporterName(req.reporterName());
        r.setReporterRole(req.reporterRole());
        r.setReporterFacilityId(req.reporterFacilityId());
        r.setReporterPhone(req.reporterPhone());
        r.setReporterEmail(req.reporterEmail());
        r.setSubjectCpid(req.subjectCpid());
        r.setSubjectIsSelf(Boolean.TRUE.equals(req.subjectIsSelf()));
        r.setSubjectInitials(req.subjectInitials());
        r.setSubjectSex(req.subjectSex());
        r.setSubjectDob(req.subjectDob());
        r.setSubjectAgeValue(req.subjectAgeValue());
        r.setSubjectAgeUnit(req.subjectAgeUnit());
        r.setSubjectWeightKg(req.subjectWeightKg());
        r.setEncounterId(req.encounterId());
        r.setNarrative(req.narrative());
        r.setOnsetDate(req.onsetDate());
        r.setSerious(Boolean.TRUE.equals(req.serious()));
        if (req.seriousnessReasons() != null) r.setSeriousnessReasons(JsonUtil.write(req.seriousnessReasons()));
        r.setSeriousnessOtherText(req.seriousnessOtherText());
        r.setFormPackKey(req.formPackKey());
        r.setFormPackVersion(req.formPackVersion());
        if (req.formData() != null) r.setFormData(JsonUtil.write(req.formData()));
    }

    private ProductExposureEntity toProduct(SafetyReportEntity r, AddProductRequest p) {
        ProductExposureEntity e = new ProductExposureEntity(r.getId(), r.getTenantId(), p.productName());
        if (p.productRole() != null && !p.productRole().isBlank()) e.setProductRole(p.productRole());
        if (p.productKind() != null && !p.productKind().isBlank()) e.setProductKind(p.productKind());
        e.setProductId(p.productId());
        e.setManufacturer(p.manufacturer());
        e.setBatchNumber(p.batchNumber());
        e.setLotNumber(p.lotNumber());
        e.setExpiryDate(p.expiryDate());
        e.setDose(p.dose());
        e.setDoseUnit(p.doseUnit());
        e.setRoute(p.route());
        e.setFrequency(p.frequency());
        e.setIndication(p.indication());
        e.setTherapyStartDate(p.therapyStartDate());
        e.setTherapyEndDate(p.therapyEndDate());
        e.setDechallenge(p.dechallenge());
        e.setRechallenge(p.rechallenge());
        e.setDoseNumber(p.doseNumber());
        e.setVaccinationDate(p.vaccinationDate());
        e.setVaccinationSite(p.vaccinationSite());
        return e;
    }

    private SafetyEventEntity toEvent(SafetyReportEntity r, AddEventRequest ev) {
        SafetyEventEntity e = new SafetyEventEntity(r.getId(), r.getTenantId(), ev.term());
        e.setMeddraCode(ev.meddraCode());
        e.setMeddraPt(ev.meddraPt());
        e.setOnsetDate(ev.onsetDate());
        e.setEndDate(ev.endDate());
        e.setSeverity(ev.severity());
        e.setOutcome(ev.outcome());
        return e;
    }

    private String nextReportReference(UUID tenantId) {
        int year = LocalDate.now().getYear();
        String prefix = "PSR-" + year + "-";
        long seq = reportRepository.countByTenantIdAndReferenceCodeStartingWith(tenantId, prefix) + 1;
        return prefix + String.format("%06d", seq);
    }

    private ReportSummary summary(SafetyReportEntity r) {
        return ReportAssembler.summary(r,
                ReportAssembler.primaryProductName(productRepository.findByReportIdOrderByCreatedAtAsc(r.getId())),
                ReportAssembler.primaryReactionTerm(eventRepository.findByReportIdOrderByCreatedAtAsc(r.getId())));
    }

    private ReportResponse assemble(SafetyReportEntity r) {
        SafetyCaseEntity c = caseRepository.findByReportId(r.getId()).orElse(null);
        return ReportAssembler.response(
                r,
                productRepository.findByReportIdOrderByCreatedAtAsc(r.getId()),
                eventRepository.findByReportIdOrderByCreatedAtAsc(r.getId()),
                attachmentRepository.findByReportIdOrderByCreatedAtAsc(r.getId()),
                c != null ? c.getId() : null,
                c != null ? c.getCaseReference() : null);
    }
}
