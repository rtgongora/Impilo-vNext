package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.integration.OrosMedicationIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicationReconciliationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicationReconciliationItemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.MedicationReconciliationItemRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MedicationReconciliationRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Medication reconciliation — comparing what the system believes against what the patient takes.
 *
 * <p>Most medication error happens at transitions, and it happens because nobody recorded that the
 * comparison was made. The properties enforced here all serve one rule: <strong>an unfinished
 * reconciliation must never read as a clean one.</strong></p>
 */
@Service
public class MedicationReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(MedicationReconciliationService.class);

    static final Set<String> CONTEXTS = Set.of(
            "ADMISSION", "DISCHARGE", "TRANSFER", "CLINIC_REVIEW", "PERIODIC_REVIEW");
    static final Set<String> FINDINGS = Set.of(
            "MATCHED", "DOSE_DIFFERS", "FREQUENCY_DIFFERS", "NOT_TAKING",
            "TAKING_UNPRESCRIBED", "DUPLICATE", "UNABLE_TO_VERIFY");
    static final Set<String> ACTIONS = Set.of(
            "CONTINUE", "STOP", "CHANGE_DOSE", "SUBSTITUTE", "ADD_TO_RECORD", "REFER_TO_PRESCRIBER");
    static final Set<String> SOURCES = Set.of(
            "PRESCRIBED", "OVER_THE_COUNTER", "TRADITIONAL", "COMPLEMENTARY", "OTHER");
    /** Findings that are open questions until an action is decided. MATCHED needs none. */
    static final Set<String> NEEDS_ACTION = Set.of(
            "DOSE_DIFFERS", "FREQUENCY_DIFFERS", "NOT_TAKING", "TAKING_UNPRESCRIBED", "DUPLICATE");

    private final MedicationReconciliationRepository reconciliationRepository;
    private final MedicationReconciliationItemRepository itemRepository;
    private final ClinicalAccessGuard accessGuard;
    private final OrosMedicationIntegration orosMedications;

    public MedicationReconciliationService(MedicationReconciliationRepository reconciliationRepository,
                                           MedicationReconciliationItemRepository itemRepository,
                                           ClinicalAccessGuard accessGuard,
                                           OrosMedicationIntegration orosMedications) {
        this.reconciliationRepository = reconciliationRepository;
        this.itemRepository = itemRepository;
        this.accessGuard = accessGuard;
        this.orosMedications = orosMedications;
    }

    @Transactional(readOnly = true)
    public List<MedicationReconciliationEntity> list(String subjectCpid) {
        return reconciliationRepository.findByTenantIdAndSubjectCpidOrderByStartedAtDesc(
                TrustContextHolder.require().tenantId(), subjectCpid);
    }

    @Transactional(readOnly = true)
    public List<MedicationReconciliationItemEntity> items(UUID reconciliationId) {
        return itemRepository.findByReconciliationId(reconciliationId);
    }

    /**
     * Opens a reconciliation and reports what the system currently believes.
     *
     * <p>When OROS cannot be reached the reconciliation still opens — a clinician in front of a
     * patient must be able to record what they are told — but {@code systemListAvailable} is false
     * so nothing downstream can read the absence of system medicines as the patient having none.</p>
     */
    @Transactional
    public Map<String, Object> start(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        MedicationReconciliationEntity r = new MedicationReconciliationEntity();
        r.setTenantId(ctx.tenantId());
        r.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        r.setJourneyId(str(body.get("journey_id"), body.get("journeyId")));
        r.setEncounterId(str(body.get("encounter_id"), body.get("encounterId")));
        accessGuard.requireCareRelationship(ctx, r.getSubjectCpid(), r.getJourneyId(), r.getEncounterId());

        String episodeId = str(body.get("episode_id"), body.get("episodeId"));
        if (episodeId != null) r.setEpisodeId(UUID.fromString(episodeId));
        r.setContext(oneOf("context", required(body, "context", "context"), CONTEXTS));
        r.setStatus("IN_PROGRESS");
        r.setSources(str(body.get("sources")));
        r.setNotes(str(body.get("notes")));
        r.setStartedBy(ctx.actorId());
        r = reconciliationRepository.save(r);

        List<String> systemCodes = orosMedications.currentMedicationCodes(ctx.tenantId(), r.getSubjectCpid());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reconciliation", r);
        // Null means OROS could not be asked. Distinct from an empty list, which means it answered
        // and the patient has nothing prescribed — one is an outage, the other a clinical finding.
        out.put("systemListAvailable", systemCodes != null);
        out.put("systemMedicationCodes", systemCodes == null ? List.of() : systemCodes);
        log.info("pct.medrec.started id={} subject={} context={} systemListAvailable={} by={}",
                r.getReconciliationId(), r.getSubjectCpid(), r.getContext(),
                systemCodes != null, ctx.actorId());
        return out;
    }

    @Transactional
    public MedicationReconciliationItemEntity addItem(UUID reconciliationId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        MedicationReconciliationEntity r = require(reconciliationId, ctx);
        if (!"IN_PROGRESS".equals(r.getStatus())) {
            throw new IllegalArgumentException(
                    "Reconciliation " + reconciliationId + " is " + r.getStatus()
                    + "; reopen it before adding findings.");
        }
        MedicationReconciliationItemEntity i = new MedicationReconciliationItemEntity();
        i.setReconciliationId(reconciliationId);
        i.setTenantId(ctx.tenantId());
        i.setDisplay(required(body, "display", "display"));
        i.setCode(str(body.get("code")));
        i.setCodeSystem(str(body.get("code_system"), body.get("codeSystem")));
        i.setPrescriptionRef(str(body.get("prescription_ref"), body.get("prescriptionRef")));
        i.setSystemSays(str(body.get("system_says"), body.get("systemSays")));
        i.setPatientSays(str(body.get("patient_says"), body.get("patientSays")));
        i.setFinding(oneOf("finding", required(body, "finding", "finding"), FINDINGS));
        i.setAction(oneOf("action", str(body.get("action")), ACTIONS));
        i.setSource(upperOr(str(body.get("source")), "PRESCRIBED", SOURCES));
        i.setNotes(str(body.get("notes")));
        return itemRepository.save(i);
    }

    /**
     * Completes the reconciliation.
     *
     * <p>Refused while any discrepancy has no decided action. "No discrepancies outstanding" is the
     * claim a completed reconciliation makes, and letting it be made over unresolved findings is
     * how a discrepancy that was noticed becomes one that was silently accepted. MATCHED and
     * UNABLE_TO_VERIFY need no action — the first because there is nothing to decide, the second
     * because recording that it could not be established IS the decision.</p>
     */
    @Transactional
    public MedicationReconciliationEntity complete(UUID reconciliationId) {
        TrustContext ctx = TrustContextHolder.require();
        MedicationReconciliationEntity r = require(reconciliationId, ctx);

        List<String> unresolved = itemRepository.findByReconciliationId(reconciliationId).stream()
                .filter(i -> NEEDS_ACTION.contains(i.getFinding()) && i.getAction() == null)
                .map(MedicationReconciliationItemEntity::getDisplay)
                .toList();
        if (!unresolved.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot complete: these discrepancies have no decided action — " + unresolved);
        }

        r.setStatus("COMPLETED");
        r.setCompletedBy(ctx.actorId());
        r.setCompletedAt(OffsetDateTime.now());
        r = reconciliationRepository.save(r);
        log.info("pct.medrec.completed id={} subject={} by={} correlationId={}",
                r.getReconciliationId(), r.getSubjectCpid(), ctx.actorId(), ctx.correlationId());
        return r;
    }

    /** Abandoning requires a reason — a reconciliation nobody could finish is a clinical fact. */
    @Transactional
    public MedicationReconciliationEntity abandon(UUID reconciliationId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        MedicationReconciliationEntity r = require(reconciliationId, ctx);
        String why = str(reason);
        if (why == null) {
            throw new IllegalArgumentException("An abandoned reconciliation must say why.");
        }
        r.setStatus("ABANDONED");
        r.setAbandonedReason(why);
        return reconciliationRepository.save(r);
    }

    private MedicationReconciliationEntity require(UUID id, TrustContext ctx) {
        MedicationReconciliationEntity r = reconciliationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation not found: " + id));
        if (!r.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Reconciliation not found: " + id);
        }
        return r;
    }

    private static String required(Map<String, Object> b, String snake, String camel) {
        String v = str(b.get(snake), b.get(camel));
        if (v == null) throw new IllegalArgumentException("Missing field: " + snake);
        return v;
    }

    private static String oneOf(String field, String value, Set<String> allowed) {
        if (value == null) return null;
        String n = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(n)) {
            throw new IllegalArgumentException("Unrecognised " + field + ": '" + value + "'. Allowed: " + allowed);
        }
        return n;
    }

    private static String upperOr(String v, String def, Set<String> allowed) {
        return v == null ? def : oneOf("source", v, allowed);
    }

    private static String str(Object... candidates) {
        for (Object v : candidates) {
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) return s;
        }
        return null;
    }
}
