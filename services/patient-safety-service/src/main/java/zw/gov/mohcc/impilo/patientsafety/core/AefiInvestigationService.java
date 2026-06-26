package zw.gov.mohcc.impilo.patientsafety.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.patientsafety.api.dto.CaseResponse.InvestigationView;
import zw.gov.mohcc.impilo.patientsafety.api.dto.OpenInvestigationRequest;
import zw.gov.mohcc.impilo.patientsafety.api.dto.UpdateInvestigationRequest;
import zw.gov.mohcc.impilo.patientsafety.core.PatientSafetyExceptions.ConflictException;
import zw.gov.mohcc.impilo.patientsafety.core.PatientSafetyExceptions.NotFoundException;
import zw.gov.mohcc.impilo.patientsafety.domain.AefiInvestigationEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.CaseActionEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.SafetyCaseEntity;
import zw.gov.mohcc.impilo.patientsafety.repository.AefiInvestigationRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.CaseActionRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.SafetyCaseRepository;
import zw.gov.mohcc.impilo.patientsafety.repository.SafetyReportRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Serious-AEFI investigation lifecycle (REQUIRED→PLANNED→IN_PROGRESS→INTERIM→FINAL→CLOSED). */
@Service
public class AefiInvestigationService {

    private static final Set<String> VALID_STATUS = Set.of(
            "NOT_REQUIRED", "REQUIRED", "PLANNED", "IN_PROGRESS", "INTERIM", "FINAL", "CLOSED");

    private final AefiInvestigationRepository investigationRepository;
    private final SafetyCaseRepository caseRepository;
    private final SafetyReportRepository reportRepository;
    private final CaseActionRepository actionRepository;
    private final SafetyEventService events;

    public AefiInvestigationService(AefiInvestigationRepository investigationRepository,
                                    SafetyCaseRepository caseRepository,
                                    SafetyReportRepository reportRepository,
                                    CaseActionRepository actionRepository,
                                    SafetyEventService events) {
        this.investigationRepository = investigationRepository;
        this.caseRepository = caseRepository;
        this.reportRepository = reportRepository;
        this.actionRepository = actionRepository;
        this.events = events;
    }

    @Transactional
    public InvestigationView open(UUID tenantId, UUID caseId, String actorId, String actorRole,
                                  UUID correlationId, OpenInvestigationRequest req) {
        SafetyCaseEntity c = requireCase(tenantId, caseId);
        if (!"AEFI".equalsIgnoreCase(c.getReportType())) {
            throw new ConflictException("Investigations apply to AEFI cases only");
        }
        if (investigationRepository.findByCaseId(caseId).isPresent()) {
            throw new ConflictException("An investigation already exists for this case");
        }
        AefiInvestigationEntity inv = new AefiInvestigationEntity(
                c.getId(), c.getTenantId(), nextInvestigationReference(tenantId));
        inv.setAssignedTo(req.assignedTo());
        inv.setPlannedDate(req.plannedDate());
        inv.setFormPackKey(req.formPackKey() != null ? req.formPackKey()
                : "patient-safety.aefi-investigation");
        inv.setStatus(req.plannedDate() != null ? "PLANNED" : "REQUIRED");
        investigationRepository.save(inv);

        CaseActionEntity a = new CaseActionEntity(c.getId(), c.getTenantId(), "INVESTIGATION_OPENED");
        a.setActorId(actorId);
        a.setActorRole(actorRole);
        a.setNote("Serious-AEFI investigation " + inv.getInvestigationReference() + " opened. " + safe(req.note()));
        actionRepository.save(a);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("case_reference", c.getCaseReference());
        payload.put("investigation_reference", inv.getInvestigationReference());
        payload.put("status", inv.getStatus());
        events.emitInvestigationOpened(inv.getId(), correlationId, c.getTenantId(), c.getPodId(),
                subjectId(c), payload);
        return view(inv);
    }

    @Transactional
    public InvestigationView update(UUID tenantId, UUID investigationId, String actorId, String actorRole,
                                    UUID correlationId, UpdateInvestigationRequest req) {
        AefiInvestigationEntity inv = investigationRepository.findByIdAndTenantId(investigationId, tenantId)
                .orElseThrow(() -> new NotFoundException("Investigation not found"));
        if (req.status() != null && !req.status().isBlank()) {
            if (!VALID_STATUS.contains(req.status())) {
                throw new IllegalArgumentException("Invalid investigation status: " + req.status());
            }
            inv.setStatus(req.status());
        }
        if (req.assignedTo() != null) inv.setAssignedTo(req.assignedTo());
        if (req.plannedDate() != null) inv.setPlannedDate(req.plannedDate());
        if (req.fieldFindings() != null) inv.setFieldFindings(JsonUtil.write(req.fieldFindings()));
        if (req.finalClassification() != null) inv.setFinalClassification(req.finalClassification());
        if (req.summary() != null) inv.setSummary(req.summary());
        inv.touch();
        investigationRepository.save(inv);

        caseRepository.findById(inv.getCaseId()).ifPresent(c -> {
            CaseActionEntity a = new CaseActionEntity(c.getId(), c.getTenantId(), "REVIEW_NOTE");
            a.setActorId(actorId);
            a.setActorRole(actorRole);
            a.setNote("Investigation " + inv.getInvestigationReference() + " → " + inv.getStatus()
                    + ". " + safe(req.note()));
            actionRepository.save(a);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("investigation_reference", inv.getInvestigationReference());
            payload.put("status", inv.getStatus());
            payload.put("final_classification", inv.getFinalClassification());
            events.emitInvestigationUpdated(inv.getId(), correlationId, c.getTenantId(), c.getPodId(),
                    subjectId(c), payload);
        });
        return view(inv);
    }

    @Transactional(readOnly = true)
    public InvestigationView getByCase(UUID tenantId, UUID caseId) {
        AefiInvestigationEntity inv = investigationRepository.findByCaseId(caseId)
                .filter(i -> i.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("No investigation for this case"));
        return view(inv);
    }

    // ---- helpers ------------------------------------------------------------

    private SafetyCaseEntity requireCase(UUID tenantId, UUID caseId) {
        return caseRepository.findByIdAndTenantId(caseId, tenantId)
                .orElseThrow(() -> new NotFoundException("Safety case not found"));
    }

    private String nextInvestigationReference(UUID tenantId) {
        int year = LocalDate.now().getYear();
        String prefix = "PSI-" + year + "-";
        long seq = investigationRepository.countByTenantIdAndInvestigationReferenceStartingWith(tenantId, prefix) + 1;
        return prefix + String.format("%06d", seq);
    }

    private String subjectId(SafetyCaseEntity c) {
        return reportRepository.findById(c.getReportId())
                .map(r -> r.getSubjectCpid() != null && !r.getSubjectCpid().isBlank()
                        ? r.getSubjectCpid() : r.getId().toString())
                .orElse(c.getId().toString());
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private InvestigationView view(AefiInvestigationEntity i) {
        return new InvestigationView(
                i.getId(), i.getInvestigationReference(), i.getStatus(), i.getAssignedTo(),
                i.getPlannedDate(), i.getFinalClassification(), i.getSummary(),
                i.getCreatedAt(), i.getUpdatedAt());
    }
}
