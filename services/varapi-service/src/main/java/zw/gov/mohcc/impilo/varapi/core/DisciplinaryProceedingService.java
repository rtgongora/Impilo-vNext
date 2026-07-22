package zw.gov.mohcc.impilo.varapi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.persistence.entity.*;
import zw.gov.mohcc.impilo.varapi.persistence.repository.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The professional disciplinary PROCEEDING FSM (ROM-W7, R5). Rito owns complaint INTAKE; the
 * proceeding lives here. Two invariants:
 *
 * <p><b>Firewall (doctrine §8):</b> a rito complaint NEVER auto-opens or auto-transitions a
 * proceeding. The only way source_rito_case_id is set is {@link #openFromReferral} — an explicit,
 * recorded action by an appointed officer. There is no rito consumer that mutates a case.</p>
 *
 * <p><b>Recusal (doctrine §4.4):</b> a person may not run a proceeding against themselves. Every
 * action asserts the officer's Health-ID != the respondent provider's Health-ID.</p>
 *
 * <p>A determination writes first-class restriction rows (V029), never a silent registration
 * mutation.</p>
 */
@Service
public class DisciplinaryProceedingService {

    public static class RecusalRequiredException extends RuntimeException {
        public RecusalRequiredException() { super("You cannot run a proceeding against yourself (recusal required)."); }
    }

    /** Legal FSM transitions. */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "RECEIVED", Set.of("PRELIMINARY_ASSESSMENT", "CLOSED"),
            "PRELIMINARY_ASSESSMENT", Set.of("UNDER_INVESTIGATION", "CLOSED"),
            "UNDER_INVESTIGATION", Set.of("CHARGES_FORMULATED", "CLOSED"),
            "CHARGES_FORMULATED", Set.of("REFERRED_TO_COMMITTEE", "CLOSED"),
            "REFERRED_TO_COMMITTEE", Set.of("HEARING", "CLOSED"),
            "HEARING", Set.of("DETERMINED"),
            "DETERMINED", Set.of("APPEALED", "CLOSED"),
            "APPEALED", Set.of("CLOSED"));

    private final ProviderDisciplinaryCaseRepository caseRepository;
    private final ProviderRepository providerRepository;
    private final ProviderCouncilRegistrationRecordRepository registrationRepository;
    private final RegisterEntryRestrictionRepository restrictionRepository;

    public DisciplinaryProceedingService(ProviderDisciplinaryCaseRepository caseRepository,
                                         ProviderRepository providerRepository,
                                         ProviderCouncilRegistrationRecordRepository registrationRepository,
                                         RegisterEntryRestrictionRepository restrictionRepository) {
        this.caseRepository = caseRepository;
        this.providerRepository = providerRepository;
        this.registrationRepository = registrationRepository;
        this.restrictionRepository = restrictionRepository;
    }

    /**
     * An appointed officer opens a proceeding — optionally from a rito referral. This is the ONLY
     * place source_rito_case_id is set; a rito event can never reach it.
     */
    @Transactional
    public ProviderDisciplinaryCaseEntity openFromReferral(UUID tenantId, String officerHealthId, Long providerId,
                                                           Long councilId, String ritoCaseId, String summary) {
        ProviderEntity subject = providerRepository.findByIdAndTenantId(providerId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));
        assertNotSelf(subject, officerHealthId);

        ProviderDisciplinaryCaseEntity c = new ProviderDisciplinaryCaseEntity();
        c.setTenantId(tenantId);
        c.setProvider(subject);
        c.setCouncilId(councilId);
        c.setTriggerType(ritoCaseId != null ? "COMPLAINT_REFERRAL" : "OFFICER_INITIATED");
        c.setSourceRitoCaseId(ritoCaseId);
        c.setSummary(summary);
        c.setStatus("OPEN");
        c.setCaseStage("RECEIVED");
        c.setOpenedBy(officerHealthId);
        c.setOpenedDate(LocalDate.now());
        return caseRepository.save(c);
    }

    /** Typed FSM transition, recusal-guarded. */
    @Transactional
    public ProviderDisciplinaryCaseEntity advance(UUID tenantId, String officerHealthId, Long caseId, String toStage) {
        ProviderDisciplinaryCaseEntity c = require(tenantId, caseId);
        assertNotSelf(c.getProvider(), officerHealthId);
        if (!TRANSITIONS.getOrDefault(c.getCaseStage(), Set.of()).contains(toStage)) {
            throw new IllegalStateException("Illegal disciplinary transition: " + c.getCaseStage() + " -> " + toStage);
        }
        c.setCaseStage(toStage);
        if ("PRELIMINARY_ASSESSMENT".equals(toStage)) {
            c.setRespondentNotified(true); // the respondent is notified when assessment opens
        }
        if ("CLOSED".equals(toStage)) {
            c.setStatus("CLOSED");
        }
        return caseRepository.save(c);
    }

    /**
     * Record the determination. When it imposes a restriction, a first-class restriction row is
     * written against the respondent's register entry for the case's council (never a silent
     * registration mutation). Recusal-guarded.
     */
    @Transactional
    public ProviderDisciplinaryCaseEntity determine(UUID tenantId, String officerHealthId, Long caseId,
                                                    String decision, String restrictionType, String restrictionDescription) {
        ProviderDisciplinaryCaseEntity c = require(tenantId, caseId);
        assertNotSelf(c.getProvider(), officerHealthId);
        if (!"HEARING".equals(c.getCaseStage())) {
            throw new IllegalStateException("A determination may only be recorded from HEARING");
        }
        c.setCaseStage("DETERMINED");
        c.setFinalDecision(decision);
        c.setFinalDecisionDate(LocalDate.now());

        if (restrictionType != null && !restrictionType.isBlank()) {
            registrationRepository.findByTenantIdAndProvider_Id(tenantId, c.getProvider().getId()).stream()
                    .filter(r -> c.getCouncilId() == null
                            || (r.getCouncil() != null && c.getCouncilId().equals(r.getCouncil().getId())))
                    .findFirst()
                    .ifPresent(record -> {
                        RegisterEntryRestrictionEntity restriction = new RegisterEntryRestrictionEntity();
                        restriction.setTenantId(tenantId);
                        restriction.setRegistrationRecordId(record.getId());
                        restriction.setRestrictionType(restrictionType);
                        restriction.setDescription(restrictionDescription != null ? restrictionDescription : decision);
                        restriction.setDecisionRef("DISCIPLINARY_CASE:" + c.getId());
                        restriction.setImposedBy(officerHealthId);
                        restriction.setValidFrom(LocalDate.now());
                        restriction.setStatus("ACTIVE");
                        restrictionRepository.save(restriction);
                    });
        }
        return caseRepository.save(c);
    }

    @Transactional(readOnly = true)
    public List<ProviderDisciplinaryCaseEntity> forProvider(UUID tenantId, Long providerId) {
        return caseRepository.findByProviderId(providerId).stream()
                .filter(c -> tenantId.equals(c.getTenantId())).toList();
    }

    void assertNotSelf(ProviderEntity subject, String officerHealthId) {
        if (subject != null && subject.getImpiloHealthId() != null && officerHealthId != null
                && subject.getImpiloHealthId().toString().equalsIgnoreCase(officerHealthId.trim())) {
            throw new RecusalRequiredException();
        }
    }

    private ProviderDisciplinaryCaseEntity require(UUID tenantId, Long caseId) {
        return caseRepository.findByIdAndTenantId(caseId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Disciplinary case not found: " + caseId));
    }
}
