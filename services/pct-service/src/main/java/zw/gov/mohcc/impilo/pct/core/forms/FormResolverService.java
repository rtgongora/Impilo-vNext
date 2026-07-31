package zw.gov.mohcc.impilo.pct.core.forms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.medicine.common.Sex;
import zw.gov.mohcc.impilo.medicine.renal.EgfrCalculator;
import zw.gov.mohcc.impilo.medicine.renal.SerumCreatinine;
import zw.gov.mohcc.impilo.paediatrics.age.AgeCalculator;
import zw.gov.mohcc.impilo.pct.core.cadre.CadreDecision;
import zw.gov.mohcc.impilo.pct.core.cadre.CadreEngine;
import zw.gov.mohcc.impilo.pct.core.cadre.CadreDecisionRequest;
import zw.gov.mohcc.impilo.pct.core.clinical.NewbornEpisodeService;
import zw.gov.mohcc.impilo.pct.integration.FormsCatalogIntegration;
import zw.gov.mohcc.impilo.pct.integration.VitoIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormResolverDecisionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.FormResolverDecisionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service around the pure {@link FormScopeEngine}. Composes {@code CadreEngine.resolve(...)} with
 * patient facts (VITO), encounter setting (PCT), and the forms-service catalog, then audits the resolution.
 * This is strictly additive to the Cadre Engine — it never mutates it.
 */
@Service
public class FormResolverService {

    private static final Logger log = LoggerFactory.getLogger(FormResolverService.class);

    private final EncounterRepository encounterRepository;
    private final VitoIntegration vitoIntegration;
    private final FormsCatalogIntegration formsCatalogIntegration;
    private final FormResolverDecisionRepository decisionRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Birth records, for gestational age at birth. Field-injected and optional so form
     * resolution — which every clinical encounter depends on — never fails because a
     * paediatric collaborator is unavailable.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NewbornEpisodeService newbornEpisodeService;

    /**
     * Pregnancy episodes, for the {@code pregnant} applicability fact. Field-injected and optional
     * for the same reason as above: form resolution underpins every clinical encounter and must not
     * fail because one collaborator is unavailable.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private zw.gov.mohcc.impilo.pct.core.clinical.PregnancyEpisodeService pregnancyEpisodeService;

    /**
     * Observations, for the adult clinical facts — weight and renal function. Field-injected and
     * optional for the same reason as the two above: form resolution underpins every clinical
     * encounter and must not fail because one collaborator is unavailable.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private zw.gov.mohcc.impilo.pct.persistence.repository.ObservationRepository observationRepository;

    /**
     * The medicine list, for interaction and duplicate-therapy checking. Field-injected and
     * optional for the same reason as the others: form resolution underpins every clinical
     * encounter and must not fail because one collaborator is unavailable.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private zw.gov.mohcc.impilo.pct.integration.OrosMedicationIntegration orosMedicationIntegration;

    public FormResolverService(EncounterRepository encounterRepository,
                               VitoIntegration vitoIntegration,
                               FormsCatalogIntegration formsCatalogIntegration,
                               FormResolverDecisionRepository decisionRepository,
                               EventOutboxRepository outboxRepository,
                               ObjectMapper objectMapper) {
        this.encounterRepository = encounterRepository;
        this.vitoIntegration = vitoIntegration;
        this.formsCatalogIntegration = formsCatalogIntegration;
        this.decisionRepository = decisionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FormResolution resolve(FormResolveInput input) {
        TrustContext ctx = TrustContextHolder.require();

        String careSetting = input.careSetting();
        String context = input.context();
        String cpid = input.cpid();
        String journeyId = null;

        if (input.encounterId() != null) {
            Optional<EncounterEntity> enc = encounterRepository.findByTenantIdAndId(ctx.tenantId(), input.encounterId());
            if (enc.isPresent()) {
                EncounterEntity e = enc.get();
                if (blank(careSetting)) {
                    careSetting = e.getCareSetting();
                }
                if (blank(context)) {
                    context = e.getEncounterContext();
                }
                if (blank(cpid)) {
                    cpid = e.getSubjectCpid();
                }
                journeyId = e.getJourneyId();
            }
        }

        // 1) Compose the Cadre Engine decision (pure; no separate cadre audit row to avoid double-audit).
        CadreDecision cadreDecision = CadreEngine.resolve(new CadreDecisionRequest(
                input.role(), input.cadre(), input.scope(), input.visitType(),
                input.acuity(), context, input.accessState()));

        // 2) Patient facts (explicit override, else VITO-derived).
        PatientFacts patient = resolvePatientFacts(input, cpid, ctx.tenantId());

        // 3) Catalog slice from forms-service (degrade-gracefully).
        List<FormCatalogEntry> catalog = formsCatalogIntegration.fetchCatalog();

        // 4) Pure resolution.
        FormResolution base = FormScopeEngine.resolve(new FormResolutionRequest(
                cadreDecision, careSetting, input.careStage(), input.specialty(),
                context, input.acuity(), patient, input.cadre(), catalog));

        // 5) Audit + outbox.
        UUID auditRef = UUID.randomUUID();
        FormResolution decided = new FormResolution(
                base.mandatory(), base.recommended(), base.optional(),
                base.prohibited(), base.countersignRequired(), auditRef.toString());

        persistAudit(ctx, auditRef, input, careSetting, context, cpid, journeyId, patient, decided);

        log.info("pct.form.resolved auditRef={} actor={} cadre={} setting={} mandatory={} prohibited={} correlationId={}",
                auditRef, ctx.actorId(), input.cadre(), careSetting,
                decided.mandatory().size(), decided.prohibited().size(), ctx.correlationId());

        return decided;
    }

    private PatientFacts resolvePatientFacts(FormResolveInput input, String cpid,
                                            java.util.UUID tenantId) {
        Integer ageMonths = input.ageMonths();
        String sex = input.sex();
        // An explicit answer from the caller always wins: a clinician who has just established
        // that a woman is pregnant should not be overruled by a record that has not caught up.
        //
        // Otherwise derive it from the pregnancy register, and derive only TRUE. FALSE is never
        // asserted from the absence of an episode: "no pregnancy has been recorded" and "she is not
        // pregnant" are different statements, and only the first is one this system can make. Until
        // now this fact had no source at all, which left the ANC form's requirePregnant gate
        // resting on whatever the caller happened to pass.
        Boolean pregnant = input.pregnant();
        if (pregnant == null) {
            pregnant = derivePregnant(tenantId, cpid);
        }
        List<String> programmes = input.programmes();
        List<String> conditions = input.conditionCodes();
        Integer ageDays = null;

        boolean needVito = (ageMonths == null || blank(sex)) && !blank(cpid);
        if (needVito) {
            Map<String, Object> demo = vitoIntegration.resolvePatientByCpid(cpid);
            if (demo != null && !demo.isEmpty()) {
                LocalDate birth = birthDate(demo);
                if (ageMonths == null) {
                    ageMonths = AgeCalculator.ageMonths(birth, LocalDate.now());
                }
                // Day-level age matters in paediatrics in a way months cannot express: the
                // sick-young-infant assessment applies to 0-59 days, and the neonatal bands
                // to the first week and the first month.
                ageDays = AgeCalculator.ageDays(birth, LocalDate.now());
                if (blank(sex)) {
                    sex = firstNonBlank(str(demo.get("gender")), str(demo.get("sex")));
                }
            }
        }

        return new PatientFacts(
                ageMonths,
                blank(sex) ? "UNKNOWN" : sex.toUpperCase(),
                pregnant,
                programmes == null ? List.of() : programmes,
                conditions == null ? List.of() : conditions,
                ageDays,
                gestationalAgeWeeks(cpid),
                latestMeasurement(tenantId, cpid, WEIGHT_CODES, "MEASURED"),
                renalFunction(tenantId, cpid, ageMonths, sex),
                // Hepatic impairment still comes from the governed Child-Pugh instrument this lane
                // owes, which does not exist yet. Null, not "NONE" — an unstaged liver is not a
                // healthy one, and a rule needing the stage must decline to score and say why.
                null,
                currentMedications(tenantId, cpid));
    }

    /**
     * The patient's current medicines, from OROS.
     *
     * <p>Returns null rather than an empty list whenever OROS could not be asked. An empty list is
     * an affirmative clinical claim — an interaction checker reads it as "nothing to interact
     * with" — so producing one from a failed call would manufacture a confident all-clear.</p>
     */
    private List<String> currentMedications(java.util.UUID tenantId, String cpid) {
        if (blank(cpid) || orosMedicationIntegration == null) {
            return null;
        }
        return orosMedicationIntegration.currentMedicationCodes(tenantId, cpid);
    }

    /** LOINC codes for body weight. */
    private static final List<String> WEIGHT_CODES = List.of("29463-7", "3141-9");
    /** LOINC codes for estimated GFR. */
    private static final List<String> EGFR_CODES = List.of("62238-1", "48642-3", "48643-1", "33914-3");

    /**
     * Renal function: the laboratory's own eGFR where one was reported, otherwise one derived from
     * a creatinine.
     *
     * <p>The stored value wins. It may come from an equation this service does not implement, and a
     * laboratory's reported rate is not something to second-guess from its own inputs.</p>
     *
     * <p>Deriving only matters because the reported rate is usually missing. A Zimbabwean laboratory
     * reports a creatinine; computing the filtration rate has been left to whoever is reading the
     * result. Every rule keyed on {@code egfr} therefore reported the fact unassessed for the
     * ordinary patient — correct behaviour on missing data, and no use at the bedside.</p>
     *
     * <p>The equation is carried in the measurement's {@code source}, which {@code toRuleFacts}
     * already emits as {@code egfrEquation}, so a consumer can tell a laboratory's figure from one
     * this platform worked out. The creatinine's own date is kept as the measurement date: a rate
     * derived from a six-month-old creatinine is six months old, however recently it was computed,
     * and content that ages facts out must see it that way.</p>
     *
     * <p>Package-private so a test can drive it against a mocked observation repository, as
     * {@code ProgrammeGuidanceService.evaluatePack} is.</p>
     */
    DatedMeasurement renalFunction(java.util.UUID tenantId, String cpid,
                                   Integer ageMonths, String sex) {
        DatedMeasurement reported = latestMeasurement(tenantId, cpid, EGFR_CODES, null);
        if (reported != null) {
            return reported;
        }
        DatedMeasurement creatinine = latestMeasurement(
                tenantId, cpid, SerumCreatinine.LOINC_CODES, null);
        if (creatinine == null || creatinine.value() == null) {
            return null;
        }
        BigDecimal micromolPerLitre = SerumCreatinine.toMicromolPerLitre(
                BigDecimal.valueOf(creatinine.value()), creatinine.unit());
        if (micromolPerLitre == null) {
            // An unrecognised unit is a data-quality problem. Reading it as µmol/L is how a normal
            // creatinine recorded in mg/L becomes a diagnosis of renal failure.
            return null;
        }
        var egfr = EgfrCalculator.fromCreatinineMicromolPerLitre(
                micromolPerLitre,
                ageMonths == null ? null : ageMonths / 12,
                Sex.parse(sex));
        if (!egfr.computed()) {
            // Unknown, not zero, and not a default. The calculator refuses by name — no age, no sex,
            // a value outside physiological range — and the fact stays absent so a rule needing it
            // declines to score rather than scoring against something invented here.
            return null;
        }
        return DatedMeasurement.of(egfr.egfr().doubleValue(), EgfrCalculator.UNIT,
                creatinine.measuredOn(), DERIVED_EGFR_SOURCE);
    }

    /** Marks a filtration rate this service computed rather than one a laboratory reported. */
    static final String DERIVED_EGFR_SOURCE = "CKD-EPI 2021 (derived from serum creatinine)";

    /**
     * The most recent observation matching any of the given codes, as a dated measurement.
     *
     * <p>Returns null rather than a zero when nothing has been recorded, and skips observations
     * carrying a {@code data_absent_reason} — an observation that says why there is no value is
     * evidence that the measurement was attempted and failed, which is not a measurement. Reading
     * one as a value would turn a documented gap into a number.</p>
     */
    private DatedMeasurement latestMeasurement(java.util.UUID tenantId, String cpid,
                                               List<String> codes, String defaultSource) {
        if (blank(cpid) || tenantId == null || observationRepository == null) {
            return null;
        }
        try {
            for (String code : codes) {
                for (var obs : observationRepository
                        .findByTenantIdAndSubjectCpidAndCodeOrderByEffectiveAtDesc(tenantId, cpid, code)) {
                    if (obs.getValueQuantity() == null) {
                        continue;
                    }
                    return DatedMeasurement.of(
                            obs.getValueQuantity().doubleValue(),
                            obs.getValueUnit(),
                            obs.getEffectiveAt() == null ? null : obs.getEffectiveAt().toLocalDate(),
                            defaultSource);
                }
            }
        } catch (RuntimeException e) {
            // Unknown, not zero. A failed lookup must leave the fact absent so a rule needing it
            // declines to score, rather than scoring against a fabricated value.
            log.debug("Could not resolve clinical measurement for form applicability: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Gestational age at birth, read from the child's birth record where one exists, so a form
     * meant for preterm infants can be selected on corrected rather than chronological age.
     * A missing birth record is the normal case for anyone not born in the system and is not
     * an error.
     */
    private Boolean derivePregnant(java.util.UUID tenantId, String cpid) {
        if (blank(cpid) || tenantId == null || pregnancyEpisodeService == null) {
            return null;
        }
        try {
            return pregnancyEpisodeService.pregnant(tenantId, cpid);
        } catch (RuntimeException e) {
            // Unknown, not false. A failed lookup must not exclude a pregnancy-scoped form.
            log.debug("Could not resolve pregnancy status for form applicability: {}", e.getMessage());
            return null;
        }
    }

    private Integer gestationalAgeWeeks(String cpid) {
        if (blank(cpid) || newbornEpisodeService == null) {
            return null;
        }
        try {
            return newbornEpisodeService.gestationalAgeWeeks(cpid);
        } catch (RuntimeException e) {
            log.debug("No gestational age available for subject: {}", e.getMessage());
            return null;
        }
    }

    private LocalDate birthDate(Map<String, Object> demo) {
        String dob = firstNonBlank(str(demo.get("dateOfBirth")), str(demo.get("dob")), str(demo.get("birthDate")));
        if (blank(dob)) {
            return null;
        }
        try {
            return LocalDate.parse(dob.length() > 10 ? dob.substring(0, 10) : dob);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void persistAudit(TrustContext ctx, UUID auditRef, FormResolveInput input,
                              String careSetting, String context, String cpid, String journeyId,
                              PatientFacts patient, FormResolution decided) {
        FormResolverDecisionEntity row = new FormResolverDecisionEntity();
        row.setAuditRef(auditRef);
        row.setTenantId(ctx.tenantId());
        row.setActorId(ctx.actorId());
        row.setCorrelationId(ctx.correlationId());
        row.setJourneyId(journeyId);
        row.setEncounterId(input.encounterId() == null ? null : String.valueOf(input.encounterId()));
        row.setInCadre(input.cadre());
        row.setInContext(context);
        row.setInCareSetting(careSetting);
        row.setInCareStage(input.careStage());
        row.setInSpecialty(input.specialty());
        row.setInAcuity(input.acuity());
        // De-PII'd patient context (age band / sex / pregnant / programmes) — never raw demographics.
        Map<String, Object> pc = new LinkedHashMap<>();
        pc.put("ageMonths", patient.ageMonths());
        // Days and gestational age are audited alongside months because they are what a
        // paediatric obligation actually turned on; without them the audit cannot explain
        // why a sick-young-infant form was or was not required.
        pc.put("ageDays", patient.ageDays());
        pc.put("gestationalAgeWeeks", patient.gestationalAgeWeeks());
        pc.put("sex", patient.sex());
        pc.put("pregnant", patient.pregnant());
        pc.put("programmes", patient.programmesOrEmpty());
        row.setInPatientContext(toJson(pc));
        row.setMandatory(toJson(decided.mandatory()));
        row.setRecommended(toJson(decided.recommended()));
        row.setOptional(toJson(decided.optional()));
        row.setProhibited(toJson(decided.prohibited()));
        row.setCountersignRequired(toJson(decided.countersignRequired()));
        decisionRepository.save(row);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("auditRef", auditRef.toString());
        payload.put("actorId", ctx.actorId());
        payload.put("encounterId", row.getEncounterId());
        payload.put("careSetting", careSetting);
        payload.put("mandatory", decided.mandatory().stream().map(FormResolution.FormObligation::formKey).toList());
        writeOutbox(ctx.tenantId(), "FORM_RESOLUTION", auditRef.toString(), "FORM_RESOLUTION_RESOLVED", toJson(payload));
    }

    private void writeOutbox(UUID tenantId, String aggregateType, String aggregateId,
                             String eventType, String payloadJson) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payloadJson);
        outbox.setTenantId(tenantId);
        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise resolver payload: {}", e.getMessage());
            return "{}";
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
