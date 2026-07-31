package zw.gov.mohcc.impilo.pct.core.clinical;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.ClerkingVisitAttestationEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ClerkingVisitAttestationRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Visit-scoped clerking stance. Never invents STILL_TRUE — stance is required on write.
 */
@Service
public class ClerkingAttestationService {

    public static final Set<String> STANCES = Set.of("STILL_TRUE", "CHANGED", "NOT_ASSESSED");
    public static final Set<String> SECTIONS = Set.of(
            "PROBLEMS", "ALLERGIES", "SOCIAL_HISTORY", "MEDICATIONS",
            "IMMUNIZATIONS", "FUNCTIONAL", "SYSTEMS_REVIEW", "PRESENTING_CONCERN");

    private final ClerkingVisitAttestationRepository repository;
    private final ClinicalAccessGuard accessGuard;

    public ClerkingAttestationService(ClerkingVisitAttestationRepository repository,
                                      ClinicalAccessGuard accessGuard) {
        this.repository = repository;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForVisit(String subjectCpid, String encounterId) {
        return repository
                .findByTenantIdAndSubjectCpidAndEncounterIdOrderByCreatedAtDesc(
                        TrustContextHolder.require().tenantId(), subjectCpid, encounterId)
                .stream().map(this::toMap).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> attest(Map<String, Object> body) {
        // Stance first — silence must fail as client validation, not as missing trust context.
        String stance = str(body.get("stance"));
        if (stance == null) {
            throw new IllegalArgumentException(
                    "stance is required. Silence is not confirmation — choose STILL_TRUE, CHANGED, or NOT_ASSESSED.");
        }
        stance = stance.toUpperCase(Locale.ROOT);
        if (!STANCES.contains(stance)) {
            throw new IllegalArgumentException("Unrecognised stance: " + stance);
        }
        String section = str(body.get("section_key"), body.get("sectionKey"));
        if (section == null) throw new IllegalArgumentException("Missing field: section_key");
        section = section.toUpperCase(Locale.ROOT);
        if (!SECTIONS.contains(section)) {
            throw new IllegalArgumentException("Unrecognised section_key: " + section);
        }

        TrustContext ctx = TrustContextHolder.require();
        ClerkingVisitAttestationEntity e = new ClerkingVisitAttestationEntity();
        e.setTenantId(ctx.tenantId());
        e.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        e.setJourneyId(str(body.get("journey_id"), body.get("journeyId")));
        e.setEncounterId(str(body.get("encounter_id"), body.get("encounterId")));
        if (e.getJourneyId() == null && e.getEncounterId() == null) {
            throw new IllegalArgumentException("journey_id or encounter_id is required (CC-5).");
        }
        accessGuard.requireCareRelationship(ctx, e.getSubjectCpid(), e.getJourneyId(), e.getEncounterId());
        e.setSectionKey(section);
        e.setStance(stance);
        e.setPriorFactRef(str(body.get("prior_fact_ref"), body.get("priorFactRef")));
        e.setNotes(str(body.get("notes")));
        e.setRecordedBy(ctx.actorId());
        return toMap(repository.save(e));
    }

    private Map<String, Object> toMap(ClerkingVisitAttestationEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("attestation_id", e.getAttestationId().toString());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("journey_id", e.getJourneyId());
        m.put("encounter_id", e.getEncounterId());
        m.put("section_key", e.getSectionKey());
        m.put("stance", e.getStance());
        m.put("prior_fact_ref", e.getPriorFactRef());
        m.put("notes", e.getNotes());
        m.put("recorded_by", e.getRecordedBy());
        m.put("created_at", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        return m;
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
