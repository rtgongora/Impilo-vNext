package zw.gov.mohcc.impilo.pct.core.forms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.core.cadre.CadreDecision;
import zw.gov.mohcc.impilo.pct.core.cadre.CadreEngine;
import zw.gov.mohcc.impilo.pct.core.cadre.CadreDecisionRequest;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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
        PatientFacts patient = resolvePatientFacts(input, cpid);

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

    private PatientFacts resolvePatientFacts(FormResolveInput input, String cpid) {
        Integer ageMonths = input.ageMonths();
        String sex = input.sex();
        Boolean pregnant = input.pregnant();
        List<String> programmes = input.programmes();
        List<String> conditions = input.conditionCodes();

        boolean needVito = (ageMonths == null || blank(sex)) && !blank(cpid);
        if (needVito) {
            Map<String, Object> demo = vitoIntegration.resolvePatientByCpid(cpid);
            if (demo != null && !demo.isEmpty()) {
                if (ageMonths == null) {
                    ageMonths = deriveAgeMonths(demo);
                }
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
                conditions == null ? List.of() : conditions);
    }

    private Integer deriveAgeMonths(Map<String, Object> demo) {
        String dob = firstNonBlank(str(demo.get("dateOfBirth")), str(demo.get("dob")), str(demo.get("birthDate")));
        if (blank(dob)) {
            return null;
        }
        try {
            LocalDate birth = LocalDate.parse(dob.length() > 10 ? dob.substring(0, 10) : dob);
            long months = ChronoUnit.MONTHS.between(birth, LocalDate.now());
            return (int) Math.max(0, months);
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
