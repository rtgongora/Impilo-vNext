package zw.gov.mohcc.impilo.coverage.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.domain.FormularyEntity;
import zw.gov.mohcc.impilo.coverage.repository.FormularyRepository;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * OF-B8 — payer-formulary resolution + governance (V020 {@code cv_formulary}).
 *
 * <p>The middle layer of the three-layer stance: ZIBO answers "is this a real
 * national medicine code", THIS answers "does this payer plan cover it, at
 * which tier, under which benefit, PA or not", and pharmacy {@code rx_formulary}
 * answers "does this facility stock it". Resolution is honest three ways:
 * an active covered row maps to a benefit; an active row with
 * {@code covered=false} is an explicit EXCLUSION; no active row (absent or
 * outside its effective window) is NOT_LISTED — never silently covered.</p>
 */
@Service
public class FormularyService {

    public enum FormularyStatus { ON_FORMULARY, EXCLUDED, NOT_LISTED }

    /** The answer for one plan-version + medication-code lookup at a point in time. */
    public record FormularyResolution(FormularyStatus status, FormularyEntity entry) {
        public static FormularyResolution notListed() {
            return new FormularyResolution(FormularyStatus.NOT_LISTED, null);
        }
    }

    private final FormularyRepository formularyRepository;
    private final CoverageEventService eventService;

    public FormularyService(FormularyRepository formularyRepository, CoverageEventService eventService) {
        this.formularyRepository = formularyRepository;
        this.eventService = eventService;
    }

    /**
     * Resolve the formulary posture of one medication code on one plan
     * version at {@code at}. When several rows are active (overlapping
     * windows), the latest {@code effectiveFrom} wins.
     */
    @Transactional(readOnly = true)
    public FormularyResolution resolve(UUID tenantId, UUID planVersionId,
                                       String medicationCode, String codingSystem, OffsetDateTime at) {
        if (planVersionId == null || medicationCode == null || medicationCode.isBlank()) {
            return FormularyResolution.notListed();
        }
        String system = codingSystem != null && !codingSystem.isBlank()
                ? codingSystem : FormularyEntity.ATC_CODING_SYSTEM;
        OffsetDateTime when = at != null ? at : OffsetDateTime.now();
        Optional<FormularyEntity> active = formularyRepository
                .findByTenantIdAndPlanVersionIdAndMedicationCodeAndCodingSystem(
                        tenantId, planVersionId, medicationCode.trim(), system)
                .stream()
                .filter(f -> f.activeAt(when))
                .max(Comparator.comparing(FormularyEntity::getEffectiveFrom));
        if (active.isEmpty()) {
            return FormularyResolution.notListed();
        }
        FormularyEntity entry = active.get();
        return new FormularyResolution(
                entry.isCovered() ? FormularyStatus.ON_FORMULARY : FormularyStatus.EXCLUDED, entry);
    }

    @Transactional(readOnly = true)
    public List<FormularyEntity> listForPlanVersion(UUID tenantId, UUID planVersionId) {
        return formularyRepository.findByTenantIdAndPlanVersionIdOrderByMedicationCodeAsc(tenantId, planVersionId);
    }

    @Transactional
    public FormularyEntity create(FormularyEntity entry, UUID correlationId) {
        FormularyEntity saved = formularyRepository.save(entry);
        emitChanged("created", saved, correlationId);
        return saved;
    }

    @Transactional
    public FormularyEntity update(UUID tenantId, UUID id, boolean covered, String tier, String copayClass,
                                  String benefitCode, boolean requiresAuthorisation,
                                  OffsetDateTime effectiveTo, String notes, UUID correlationId) {
        FormularyEntity entry = formularyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Formulary entry not found: " + id));
        entry.setCovered(covered);
        if (tier != null && !tier.isBlank()) {
            entry.setTier(tier);
        }
        entry.setCopayClass(copayClass);
        if (benefitCode != null && !benefitCode.isBlank()) {
            entry.setBenefitCode(benefitCode);
        }
        entry.setRequiresAuthorisation(requiresAuthorisation);
        entry.setEffectiveTo(effectiveTo);
        entry.setNotes(notes);
        entry.touch();
        FormularyEntity saved = formularyRepository.save(entry);
        emitChanged("updated", saved, correlationId);
        return saved;
    }

    /**
     * Governance delete = end-date, never a hard erase: the row stays for
     * audit and past-estimate traceability, its window simply closes now.
     */
    @Transactional
    public FormularyEntity retire(UUID tenantId, UUID id, UUID correlationId) {
        FormularyEntity entry = formularyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Formulary entry not found: " + id));
        entry.setEffectiveTo(OffsetDateTime.now());
        entry.touch();
        FormularyEntity saved = formularyRepository.save(entry);
        emitChanged("retired", saved, correlationId);
        return saved;
    }

    private void emitChanged(String action, FormularyEntity entry, UUID correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("formulary_id", entry.getId().toString());
        payload.put("plan_version_id", entry.getPlanVersionId().toString());
        payload.put("medication_code", entry.getMedicationCode());
        payload.put("coding_system", entry.getCodingSystem());
        payload.put("benefit_code", entry.getBenefitCode());
        payload.put("covered", entry.isCovered());
        payload.put("tier", entry.getTier());
        payload.put("requires_authorisation", entry.isRequiresAuthorisation());
        payload.put("action", action);
        eventService.emitDomain("FORMULARY", entry.getId().toString(),
                "coverage.formulary." + action, correlationId, entry.getTenantId(), entry.getPodId(),
                entry.getPlanVersionId().toString(), "PLAN_VERSION", payload);
    }
}
