package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ExaminationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ExaminationFindingEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ExaminationFindingRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ExaminationRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Structured physical examinations (brief.md §7).
 *
 * <p><strong>The property this service exists to hold.</strong> A region that was not examined must
 * never be readable as one that was examined and found normal. The database enforces the vocabulary;
 * this service enforces the two things a CHECK cannot see — that the caller's state and detail are
 * coherent before the write, so the clinician gets a 400 naming the problem rather than a constraint
 * violation, and that a read reports what was <em>not</em> covered as prominently as what was.</p>
 */
@Service
public class ExaminationService {

    private static final Logger log = LoggerFactory.getLogger(ExaminationService.class);

    /** Event type routed to {@code pct.examination.recorded} by {@code OutboxPublisher.routeTopic}. */
    static final String EVENT_EXAMINATION_RECORDED = "EXAMINATION_RECORDED";

    private final ExaminationRepository examinationRepository;
    private final ExaminationFindingRepository findingRepository;
    private final ClinicalAccessGuard accessGuard;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ExaminationService(ExaminationRepository examinationRepository,
                              ExaminationFindingRepository findingRepository,
                              ClinicalAccessGuard accessGuard,
                              EventOutboxRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.examinationRepository = examinationRepository;
        this.findingRepository = findingRepository;
        this.accessGuard = accessGuard;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public ExaminationEntity record(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String subjectCpid = required(body, "subject_cpid", "subjectCpid");

        String journeyRaw = str(body.get("journey_id"), body.get("journeyId"));
        String encounterRaw = str(body.get("encounter_id"), body.get("encounterId"));
        accessGuard.requireCareRelationship(ctx, subjectCpid, journeyRaw, encounterRaw);

        UUID journeyId = uuid(journeyRaw);
        UUID encounterId = uuid(encounterRaw);
        if (journeyId == null && encounterId == null) {
            // care-continuum-doctrine CC-5. Refused here as well as by the CHECK so the message names
            // the doctrine rather than the constraint.
            throw new IllegalArgumentException(
                    "An examination must carry a journey_id or an encounter_id. A clinical record "
                    + "with no resolvable PCT anchor cannot be placed in the patient's journey.");
        }

        // Offline replay is idempotent on the client's id: a retried sync must not create a second
        // examination of the same patient at the same moment.
        String offlineId = str(body.get("client_offline_id"), body.get("clientOfflineId"));
        if (offlineId != null) {
            var existing = examinationRepository.findByTenantIdAndClientOfflineId(ctx.tenantId(), offlineId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Object rawFindings = body.get("findings");
        if (!(rawFindings instanceof List<?> list) || list.isEmpty()) {
            // An examination with no findings at all is not a record of an examination; it is a
            // timestamp. Refusing it keeps the timeline free of entries that imply a look nobody took.
            throw new IllegalArgumentException(
                    "An examination must record at least one region — including regions recorded as "
                    + "NOT_EXAMINED, which is a finding in its own right.");
        }

        ExaminationEntity e = new ExaminationEntity();
        e.setExaminationId(UUID.randomUUID());
        e.setTenantId(ctx.tenantId());
        e.setSubjectCpid(subjectCpid);
        e.setJourneyId(journeyId);
        e.setEncounterId(encounterId);
        e.setExaminedBy(ctx.actorId());
        e.setExaminedAt(OffsetDateTime.now());
        e.setFacilityId(str(body.get("facility_id"), body.get("facilityId")));
        e.setContextNote(str(body.get("context_note"), body.get("contextNote")));
        e.setClientOfflineId(offlineId);
        examinationRepository.save(e);

        Set<String> seen = new LinkedHashSet<>();
        List<ExaminationFindingEntity> findings = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Each finding must be an object.");
            }
            Map<String, Object> f = (Map<String, Object>) raw;
            findings.add(finding(ctx, e.getExaminationId(), f, seen));
        }
        findingRepository.saveAll(findings);

        emit(ctx, e, findings);

        log.info("pct.examination.recorded id={} subject={} regions={} unexamined={} by={} correlationId={}",
                e.getExaminationId(), subjectCpid, findings.size(),
                findings.stream().filter(x -> !ExaminationVocabulary.wasExamined(x.getState())).count(),
                ctx.actorId(), ctx.correlationId());
        return e;
    }

    private ExaminationFindingEntity finding(TrustContext ctx, UUID examinationId,
                                             Map<String, Object> f, Set<String> seen) {
        String region = ProblemVocabulary.require("region", str(f.get("region")),
                ExaminationVocabulary.REGIONS, true);
        if (!seen.add(region)) {
            // The second row would silently win any read that takes the latest, so the same region
            // recorded twice in one examination is refused rather than resolved.
            throw new IllegalArgumentException("Region " + region + " is recorded twice in this examination.");
        }
        String state = ProblemVocabulary.require("state", str(f.get("state")),
                ExaminationVocabulary.STATES, true);
        String detail = str(f.get("detail"));

        if (ExaminationVocabulary.requiresDetail(state) && (detail == null || detail.isBlank())) {
            throw new IllegalArgumentException(
                    "State " + state + " for " + region + " must say "
                    + (ExaminationVocabulary.wasExamined(state) ? "what was found" : "why not")
                    + ". An abnormality nobody wrote down reads as a normal examination somebody "
                    + "bothered to document.");
        }

        String graphic = f.get("graphic") == null ? null : ProblemVocabulary.require("graphic",
                str(f.get("graphic")), ExaminationVocabulary.GRAPHICS, true);
        String laterality = f.get("laterality") == null ? null : ProblemVocabulary.require("laterality",
                str(f.get("laterality")), ExaminationVocabulary.LATERALITY, true);
        String site = str(f.get("site"));
        if (site != null && graphic == null) {
            throw new IllegalArgumentException(
                    "A site needs a graphic: '" + site + "' is a coordinate with no map, and cannot "
                    + "be drawn or read back.");
        }

        ExaminationFindingEntity entity = new ExaminationFindingEntity();
        entity.setFindingId(UUID.randomUUID());
        entity.setTenantId(ctx.tenantId());
        entity.setExaminationId(examinationId);
        entity.setRegion(region);
        entity.setState(state);
        entity.setDetail(detail);
        entity.setFindingCode(str(f.get("finding_code"), f.get("findingCode")));
        entity.setGraphic(graphic);
        entity.setSite(site);
        entity.setLaterality(laterality);
        return entity;
    }

    /**
     * Writes the examination onto the outbox so BUTANO can archive it as a FHIR ClinicalImpression
     * (brief.md §19).
     *
     * <p>Until this existed an examination was visible only inside PCT. A clinician at the next
     * facility reading the shared record saw the diagnosis (Condition) and the vitals (Observation)
     * but no trace of the examination that produced them — including, critically, no trace of the
     * regions that were never looked at.</p>
     *
     * <p>Mirrors {@code ProblemService.emit}: same table, same outbox pattern, same transaction as
     * the domain write. No second event spine.</p>
     */
    private void emit(TrustContext ctx, ExaminationEntity e, List<ExaminationFindingEntity> findings) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("EXAMINATION");
        outbox.setAggregateId(e.getExaminationId().toString());
        outbox.setEventType(EVENT_EXAMINATION_RECORDED);
        outbox.setPayload(toJson(shrPayload(e, findings, ctx.actorId())));
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);
    }

    /**
     * The exact set of fields that crosses into the shared health record.
     *
     * <p><strong>Free text is deliberately absent.</strong> {@code detail} is the one field on a
     * finding that a clinician types in prose — "coarse crackles, husband says she collapsed at the
     * Mbare bus rank" — so it is the field that can carry a name, a phone number or an address into a
     * record whose whole contract is that identifying data stays in VITO. {@code context_note} and
     * {@code site} are prose for the same reason. Only the closed-vocabulary region, the closed-
     * vocabulary state and the coded finding travel. The same rule the Condition producer applies to
     * {@code notes}, applied to the field that holds prose here.</p>
     *
     * <p><strong>The state travels verbatim, per region.</strong> Collapsing the six states into a
     * two-state normal/abnormal shape at this boundary would undo the whole point of §7: a region
     * nobody looked at would arrive in the SHR indistinguishable from one that was examined and found
     * normal, and the SHR is precisely where the next clinician decides whether to look again.</p>
     *
     * <p>Package-private and static so the boundary can be asserted without a running service.</p>
     */
    static Map<String, Object> shrPayload(ExaminationEntity e, List<ExaminationFindingEntity> findings,
                                          String actorId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ExaminationFindingEntity f : findings) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("region", f.getRegion());
            row.put("state", f.getState());
            row.put("findingCode", f.getFindingCode());
            rows.add(row);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("examinationId", e.getExaminationId().toString());
        payload.put("subjectCpid", e.getSubjectCpid());
        payload.put("journeyId", e.getJourneyId() == null ? null : e.getJourneyId().toString());
        payload.put("encounterId", e.getEncounterId() == null ? null : e.getEncounterId().toString());
        payload.put("examinedAt", e.getExaminedAt() == null ? null : e.getExaminedAt().toString());
        payload.put("actorId", actorId);
        payload.put("findings", rows);
        return payload;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            // Never a silent "{}": an empty payload would publish successfully and archive an
            // examination with no findings at all, which reads as a look that found nothing.
            log.error("Failed to serialise examination payload; the SHR event is not emitted: {}",
                    ex.getMessage());
            throw new IllegalStateException("Could not serialise the examination for the shared record", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<ExaminationEntity> list(String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        return examinationRepository.findByTenantIdAndSubjectCpidOrderByExaminedAtDesc(
                ctx.tenantId(), subjectCpid);
    }

    /**
     * One examination with its findings, and an explicit account of coverage.
     *
     * <p>The coverage block is the read-side half of the framework's purpose. A reader scanning a
     * list of findings sees what was looked at; they do not see what was not, because absence has no
     * row. Naming the regions that were never considered, alongside those explicitly recorded as
     * unexamined, is what stops a partial examination reading as a complete one.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID examinationId) {
        TrustContext ctx = TrustContextHolder.require();
        ExaminationEntity e = examinationRepository
                .findByTenantIdAndExaminationId(ctx.tenantId(), examinationId)
                .orElseThrow(() -> new IllegalArgumentException("Examination not found: " + examinationId));

        List<ExaminationFindingEntity> findings =
                findingRepository.findByTenantIdAndExaminationIdOrderByRegionAsc(ctx.tenantId(), examinationId);

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> examined = new ArrayList<>();
        List<String> recordedUnexamined = new ArrayList<>();
        for (ExaminationFindingEntity f : findings) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("region", f.getRegion());
            row.put("state", f.getState());
            row.put("was_examined", ExaminationVocabulary.wasExamined(f.getState()));
            row.put("detail", f.getDetail());
            row.put("finding_code", f.getFindingCode());
            row.put("graphic", f.getGraphic());
            row.put("site", f.getSite());
            row.put("laterality", f.getLaterality());
            rows.add(row);
            if (ExaminationVocabulary.wasExamined(f.getState())) {
                examined.add(f.getRegion());
            } else {
                recordedUnexamined.add(f.getRegion());
            }
        }

        Set<String> covered = new LinkedHashSet<>(examined);
        covered.addAll(recordedUnexamined);
        List<String> neverConsidered = ExaminationVocabulary.REGIONS.stream()
                .filter(r -> !covered.contains(r)).sorted().toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("examination_id", e.getExaminationId());
        out.put("subject_cpid", e.getSubjectCpid());
        out.put("journey_id", e.getJourneyId());
        out.put("encounter_id", e.getEncounterId());
        out.put("examined_at", e.getExaminedAt());
        out.put("examined_by", e.getExaminedBy());
        out.put("context_note", e.getContextNote());
        out.put("findings", rows);
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("examined", examined);
        coverage.put("recorded_not_examined", recordedUnexamined);
        coverage.put("never_considered", neverConsidered);
        coverage.put("note",
                examined.size() + " of " + ExaminationVocabulary.REGIONS.size() + " regions were "
                + "examined. " + recordedUnexamined.size() + " were explicitly recorded as not "
                + "examined and " + neverConsidered.size() + " were never considered. Neither of "
                + "those two groups is a normal finding.");
        out.put("coverage", coverage);
        return out;
    }

    private static String required(Map<String, Object> body, String snake, String camel) {
        String v = str(body.get(snake), body.get(camel));
        if (v == null) {
            throw new IllegalArgumentException("Missing required field: " + snake);
        }
        return v;
    }

    private static String str(Object... candidates) {
        for (Object o : candidates) {
            if (o != null && !o.toString().isBlank()) {
                return o.toString().trim();
            }
        }
        return null;
    }

    private static UUID uuid(String raw) {
        return raw == null ? null : UUID.fromString(raw);
    }
}
