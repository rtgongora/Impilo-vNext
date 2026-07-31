package zw.gov.mohcc.impilo.pct.core.clinical;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.AdultJourneyPositionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.AdultJourneyPositionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Adult medicine journey position (brief.md §5) — where the patient is on the 17-step pathway.
 */
@Service
public class AdultJourneyPositionService {

    public static final List<String> STEP_KEYS = List.of(
            "PRESENTING", "CLERKING", "EXAMINATION", "PROBLEMS", "INVESTIGATIONS", "DIAGNOSIS",
            "TREATMENT_PLAN", "MEDICINES", "PROCEDURES", "ADMISSION", "WARD_ROUND", "CONSULTATION",
            "MDT", "MONITORING", "DISCHARGE_PLANNING", "TRANSFER", "FOLLOW_UP");

    private static final Set<String> STEP_KEY_SET = Set.copyOf(STEP_KEYS);

    private final AdultJourneyPositionRepository repository;
    private final ClinicalAccessGuard accessGuard;

    public AdultJourneyPositionService(AdultJourneyPositionRepository repository,
                                       ClinicalAccessGuard accessGuard) {
        this.repository = repository;
        this.accessGuard = accessGuard;
    }

    public static int indexForKey(String stepKey) {
        if (stepKey == null) return -1;
        int idx = STEP_KEYS.indexOf(stepKey.toUpperCase(Locale.ROOT));
        return idx >= 0 ? idx + 1 : -1;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> current(String subjectCpid, String journeyId, String encounterId) {
        TrustContext ctx = TrustContextHolder.require();
        AdultJourneyPositionEntity latest = resolveLatest(ctx.tenantId(), subjectCpid, journeyId, encounterId)
                .orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subject_cpid", subjectCpid);
        out.put("step_keys", STEP_KEYS);
        if (latest == null) {
            out.put("position", null);
            out.put("position_note",
                    "No journey position recorded. This is not a statement that the patient has not "
                    + "started the pathway.");
            return out;
        }
        out.put("position", toMap(latest));
        return out;
    }

    @Transactional
    public Map<String, Object> record(Map<String, Object> body) {
        String subjectCpid = required(body, "subject_cpid", "subjectCpid");
        String stepKeyRaw = required(body, "step_key", "stepKey");
        String stepKey = stepKeyRaw.toUpperCase(Locale.ROOT);
        if (!STEP_KEY_SET.contains(stepKey)) {
            throw new IllegalArgumentException("Unrecognised step_key: " + stepKeyRaw);
        }

        Object indexRaw = body.get("step_index") != null ? body.get("step_index") : body.get("stepIndex");
        if (indexRaw == null) {
            throw new IllegalArgumentException("Missing field: step_index");
        }
        int stepIndex = parseIndex(indexRaw);
        if (stepIndex < 1 || stepIndex > 17) {
            throw new IllegalArgumentException("step_index must be between 1 and 17");
        }
        int expected = indexForKey(stepKey);
        if (stepIndex != expected) {
            throw new IllegalArgumentException(
                    "step_index " + stepIndex + " does not match step_key " + stepKey + " (expected "
                    + expected + ")");
        }

        TrustContext ctx = TrustContextHolder.require();
        String journeyId = str(body.get("journey_id"), body.get("journeyId"));
        String encounterId = str(body.get("encounter_id"), body.get("encounterId"));
        if (journeyId == null && encounterId == null) {
            throw new IllegalArgumentException("journey_id or encounter_id is required (CC-5).");
        }
        accessGuard.requireCareRelationship(ctx, subjectCpid, journeyId, encounterId);

        AdultJourneyPositionEntity e = new AdultJourneyPositionEntity();
        e.setTenantId(ctx.tenantId());
        e.setSubjectCpid(subjectCpid);
        e.setJourneyId(journeyId);
        e.setEncounterId(encounterId);
        e.setStepKey(stepKey);
        e.setStepIndex(stepIndex);
        e.setNotes(str(body.get("notes")));
        e.setRecordedBy(ctx.actorId());
        return toMap(repository.save(e));
    }

    private java.util.Optional<AdultJourneyPositionEntity> resolveLatest(
            UUID tenantId, String subjectCpid, String journeyId, String encounterId) {
        if (encounterId != null && !encounterId.isBlank()) {
            return repository.findFirstByTenantIdAndSubjectCpidAndEncounterIdOrderByRecordedAtDesc(
                    tenantId, subjectCpid, encounterId);
        }
        if (journeyId != null && !journeyId.isBlank()) {
            return repository.findFirstByTenantIdAndSubjectCpidAndJourneyIdOrderByRecordedAtDesc(
                    tenantId, subjectCpid, journeyId);
        }
        return repository.findFirstByTenantIdAndSubjectCpidOrderByRecordedAtDesc(tenantId, subjectCpid);
    }

    private static Map<String, Object> toMap(AdultJourneyPositionEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("journey_id", e.getJourneyId());
        m.put("encounter_id", e.getEncounterId());
        m.put("step_key", e.getStepKey());
        m.put("step_index", e.getStepIndex());
        m.put("notes", e.getNotes());
        m.put("recorded_by", e.getRecordedBy());
        m.put("recorded_at", e.getRecordedAt() == null ? null : e.getRecordedAt().toString());
        return m;
    }

    private static int parseIndex(Object raw) {
        if (raw instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(raw).trim());
    }

    private static String required(Map<String, Object> body, String snake, String camel) {
        String v = str(body.get(snake), body.get(camel));
        if (v == null) throw new IllegalArgumentException("Missing field: " + snake);
        return v;
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
