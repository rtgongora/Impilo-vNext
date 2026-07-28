package zw.gov.mohcc.impilo.surv.core;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surv.persistence.entity.CaseContactEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.CaseEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.CaseUpdateEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.CaseContactRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.CaseRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.CaseUpdateRepository;

/**
 * Case investigation, contact tracing and the line list.
 *
 * <p>These back three controls that existed on the surveillance panel with no handler and nowhere
 * for their data to go: "Save Update", "Link Contacts" and "Generate Line List". A public-health
 * officer could click "Link Contacts" during an outbreak, see no error, and conclude tracing had
 * begun. Nobody was being traced.
 */
@Service
public class CaseInvestigationService {

    /**
     * Closed vocabularies. An unrecognised value is REJECTED rather than stored: a line list is
     * aggregated and acted on, and one row saying "Confirmed" where every other says "CONFIRMED"
     * silently drops out of the count that triggers a response.
     */
    private static final Set<String> CLASSIFICATIONS =
            Set.of("SUSPECTED", "PROBABLE", "CONFIRMED", "DISCARDED");
    private static final Set<String> OUTCOMES =
            Set.of("ADMITTED", "RECOVERING", "DISCHARGED", "DECEASED");
    private static final Set<String> LAB_RESULTS =
            Set.of("PENDING", "POSITIVE", "NEGATIVE", "INCONCLUSIVE");
    private static final Set<String> TRACE_STATUSES =
            Set.of("PENDING", "CONTACTED", "MONITORING", "COMPLETED", "LOST_TO_FOLLOW_UP");

    private final CaseRepository caseRepository;
    private final CaseUpdateRepository updateRepository;
    private final CaseContactRepository contactRepository;

    public CaseInvestigationService(CaseRepository caseRepository,
                                    CaseUpdateRepository updateRepository,
                                    CaseContactRepository contactRepository) {
        this.caseRepository = caseRepository;
        this.updateRepository = updateRepository;
        this.contactRepository = contactRepository;
    }

    /**
     * Records an investigation update and mirrors the current values onto the case.
     *
     * <p>Every supplied field is validated before anything is written. A partial update is allowed
     * — a lab result can arrive without an outcome — but an unrecognised value fails the whole
     * call rather than being stored or silently dropped.
     */
    @Transactional
    public CaseUpdateEntity recordUpdate(UUID tenantId, Long caseId, String classification,
                                         String outcome, String labResult, String notes,
                                         String actorId) {
        CaseEntity found = caseRepository.findById(caseId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("No such case for this tenant: " + caseId));

        String cls = normalise(classification, CLASSIFICATIONS, "classification");
        String out = normalise(outcome, OUTCOMES, "outcome");
        String lab = normalise(labResult, LAB_RESULTS, "lab_result");

        if (cls == null && out == null && lab == null && (notes == null || notes.isBlank())) {
            throw new IllegalArgumentException(
                    "An update must change something — supply a classification, outcome, lab result or note.");
        }

        CaseUpdateEntity update = new CaseUpdateEntity();
        update.setTenantId(tenantId);
        update.setCaseId(caseId);
        update.setClassification(cls);
        update.setOutcome(out);
        update.setLabResult(lab);
        update.setNotes(notes);
        update.setRecordedBy(actorId);
        update = updateRepository.save(update);

        // Mirror only what this update actually stated. A null here means "not restated", not
        // "cleared" — overwriting a confirmed classification with null because the clinician only
        // entered a lab result would silently downgrade the case.
        if (cls != null) found.setClassification(cls);
        if (out != null) found.setOutcome(out);
        if (lab != null) found.setLabResult(lab);
        caseRepository.save(found);

        return update;
    }

    @Transactional(readOnly = true)
    public List<CaseUpdateEntity> updates(UUID tenantId, Long caseId) {
        return updateRepository.findByTenantIdAndCaseIdOrderByRecordedAtDesc(tenantId, caseId);
    }

    /** Links a traced contact to a case. Identity is optional — see CaseContactEntity. */
    @Transactional
    public CaseContactEntity linkContact(UUID tenantId, Long caseId, String name, String phone,
                                         String cpid, String relationship, java.time.LocalDate exposureDate,
                                         String notes, String actorId) {
        caseRepository.findById(caseId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("No such case for this tenant: " + caseId));

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A contact needs a name — it is how the tracing team finds them.");
        }

        CaseContactEntity contact = new CaseContactEntity();
        contact.setTenantId(tenantId);
        contact.setCaseId(caseId);
        contact.setContactName(name.trim());
        contact.setContactPhone(phone);
        contact.setContactCpid(cpid);
        contact.setRelationship(relationship);
        contact.setExposureDate(exposureDate);
        contact.setNotes(notes);
        contact.setCreatedBy(actorId);
        return contactRepository.save(contact);
    }

    @Transactional(readOnly = true)
    public List<CaseContactEntity> contacts(UUID tenantId, Long caseId) {
        return contactRepository.findByTenantIdAndCaseIdOrderByCreatedAtDesc(tenantId, caseId);
    }

    @Transactional
    public CaseContactEntity updateTraceStatus(UUID tenantId, Long contactId, String traceStatus) {
        String status = normalise(traceStatus, TRACE_STATUSES, "trace_status");
        if (status == null) {
            throw new IllegalArgumentException("A trace status is required.");
        }
        CaseContactEntity contact = contactRepository.findById(contactId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("No such contact for this tenant: " + contactId));
        contact.setTraceStatus(status);
        contact.setLastContactedAt(OffsetDateTime.now());
        return contactRepository.save(contact);
    }

    /**
     * The line list: one row per case, with the fields an outbreak response reads.
     *
     * <p>Contact counts are included because "how many contacts has this case generated" is the
     * question the list is usually opened to answer, and computing it per row downstream is how a
     * team ends up with two different numbers in the same meeting.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> lineList(UUID tenantId, String caseType) {
        List<CaseEntity> cases = caseType == null || caseType.isBlank()
                ? caseRepository.findByTenantId(tenantId)
                : caseRepository.findByTenantIdAndCaseType(tenantId, caseType);

        return cases.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("case_id", c.getId());
            row.put("case_type", c.getCaseType());
            row.put("title", c.getTitle());
            row.put("status", c.getStatus() == null ? null : c.getStatus().name());
            row.put("severity", c.getSeverity() == null ? null : c.getSeverity().name());
            row.put("classification", c.getClassification());
            row.put("outcome", c.getOutcome());
            row.put("lab_result", c.getLabResult());
            row.put("facility_id", c.getFacilityId());
            row.put("assigned_to", c.getAssignedTo());
            row.put("opened_at", c.getCreatedAt() == null ? null : c.getCreatedAt().toString());
            row.put("contact_count", contactRepository.countByTenantIdAndCaseId(tenantId, c.getId()));
            return row;
        }).toList();
    }

    /** Upper-cases and validates against a closed set; null/blank means "not stated". */
    private static String normalise(String value, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!allowed.contains(v)) {
            throw new IllegalArgumentException(
                    "Unrecognised " + field + " '" + value + "'. Allowed: " + allowed);
        }
        return v;
    }
}
