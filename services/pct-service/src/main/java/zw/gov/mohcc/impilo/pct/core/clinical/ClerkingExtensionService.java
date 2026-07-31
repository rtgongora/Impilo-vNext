package zw.gov.mohcc.impilo.pct.core.clinical;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.*;
import zw.gov.mohcc.impilo.pct.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wave 3 NEW_PCT clerking facts — presenting concern, ROS, prior ICU, infection exposure,
 * cognition/mood screens, genetic risk, safeguarding (confidential lane).
 */
@Service
public class ClerkingExtensionService {

    private static final Set<String> PRESENTING_SOURCES = Set.of("AMBULATORY", "ED_INTEGRATE");

    private final PresentingConcernRepository presentingConcernRepository;
    private final SystemsReviewRepository systemsReviewRepository;
    private final PriorIcuHistoryRepository priorIcuHistoryRepository;
    private final InfectionExposureRepository infectionExposureRepository;
    private final CognitionMoodScreenRepository cognitionMoodScreenRepository;
    private final GeneticRiskRepository geneticRiskRepository;
    private final SafeguardingDisclosureRepository safeguardingDisclosureRepository;
    private final ClinicalAccessGuard accessGuard;

    public ClerkingExtensionService(PresentingConcernRepository presentingConcernRepository,
                                    SystemsReviewRepository systemsReviewRepository,
                                    PriorIcuHistoryRepository priorIcuHistoryRepository,
                                    InfectionExposureRepository infectionExposureRepository,
                                    CognitionMoodScreenRepository cognitionMoodScreenRepository,
                                    GeneticRiskRepository geneticRiskRepository,
                                    SafeguardingDisclosureRepository safeguardingDisclosureRepository,
                                    ClinicalAccessGuard accessGuard) {
        this.presentingConcernRepository = presentingConcernRepository;
        this.systemsReviewRepository = systemsReviewRepository;
        this.priorIcuHistoryRepository = priorIcuHistoryRepository;
        this.infectionExposureRepository = infectionExposureRepository;
        this.cognitionMoodScreenRepository = cognitionMoodScreenRepository;
        this.geneticRiskRepository = geneticRiskRepository;
        this.safeguardingDisclosureRepository = safeguardingDisclosureRepository;
        this.accessGuard = accessGuard;
    }

    private TrustContext ctx() { return TrustContextHolder.require(); }

    // ── Presenting concern ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPresentingConcerns(String cpid) {
        return presentingConcernRepository
                .findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(ctx().tenantId(), cpid)
                .stream().map(this::mapPresenting).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> recordPresentingConcern(Map<String, Object> body) {
        TrustContext c = ctx();
        PresentingConcernEntity e = new PresentingConcernEntity();
        e.setTenantId(c.tenantId());
        e.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        e.setJourneyId(str(body.get("journey_id"), body.get("journeyId")));
        e.setEncounterId(str(body.get("encounter_id"), body.get("encounterId")));
        requireAnchor(e.getJourneyId(), e.getEncounterId());
        accessGuard.requireCareRelationship(c, e.getSubjectCpid(), e.getJourneyId(), e.getEncounterId());
        e.setConcernText(required(body, "concern_text", "concernText"));
        String onset = str(body.get("onset_at"), body.get("onsetAt"));
        if (onset != null) e.setOnsetAt(OffsetDateTime.parse(onset));
        e.setTimelineJson(str(body.get("timeline_json"), body.get("timelineJson")));
        String source = str(body.get("source"));
        e.setSource(source == null ? "AMBULATORY" : oneOf("source", source, PRESENTING_SOURCES));
        e.setRecordedBy(c.actorId());
        return mapPresenting(presentingConcernRepository.save(e));
    }

    // ── Systems review ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSystemsReviews(String cpid) {
        return systemsReviewRepository
                .findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(ctx().tenantId(), cpid)
                .stream().map(this::mapSystems).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> recordSystemsReview(Map<String, Object> body) {
        TrustContext c = ctx();
        SystemsReviewEntity e = new SystemsReviewEntity();
        e.setTenantId(c.tenantId());
        e.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        e.setJourneyId(str(body.get("journey_id"), body.get("journeyId")));
        e.setEncounterId(str(body.get("encounter_id"), body.get("encounterId")));
        requireAnchor(e.getJourneyId(), e.getEncounterId());
        accessGuard.requireCareRelationship(c, e.getSubjectCpid(), e.getJourneyId(), e.getEncounterId());
        e.setSystemsJson(required(body, "systems_json", "systemsJson"));
        e.setRecordedBy(c.actorId());
        return mapSystems(systemsReviewRepository.save(e));
    }

    // ── Prior ICU ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPriorIcu(String cpid) {
        return priorIcuHistoryRepository
                .findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(ctx().tenantId(), cpid)
                .stream().map(this::mapPriorIcu).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> recordPriorIcu(Map<String, Object> body) {
        TrustContext c = ctx();
        PriorIcuHistoryEntity e = new PriorIcuHistoryEntity();
        e.setTenantId(c.tenantId());
        e.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        accessGuard.requireCareRelationship(c, e.getSubjectCpid(), null, null);
        String year = str(body.get("approx_year"), body.get("approxYear"));
        if (year != null) e.setApproxYear(Integer.valueOf(year));
        e.setFacilityNote(str(body.get("facility_note"), body.get("facilityNote")));
        e.setReason(str(body.get("reason")));
        e.setRecordedBy(c.actorId());
        return mapPriorIcu(priorIcuHistoryRepository.save(e));
    }

    // ── Infection exposure ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listInfectionExposures(String cpid) {
        return infectionExposureRepository
                .findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(ctx().tenantId(), cpid)
                .stream().map(this::mapExposure).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> recordInfectionExposure(Map<String, Object> body) {
        TrustContext c = ctx();
        InfectionExposureEntity e = new InfectionExposureEntity();
        e.setTenantId(c.tenantId());
        e.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        e.setJourneyId(str(body.get("journey_id"), body.get("journeyId")));
        e.setEncounterId(str(body.get("encounter_id"), body.get("encounterId")));
        accessGuard.requireCareRelationship(c, e.getSubjectCpid(), e.getJourneyId(), e.getEncounterId());
        e.setExposureType(required(body, "exposure_type", "exposureType"));
        e.setDetail(str(body.get("detail")));
        String on = str(body.get("exposed_on"), body.get("exposedOn"));
        if (on != null) e.setExposedOn(LocalDate.parse(on));
        e.setRecordedBy(c.actorId());
        return mapExposure(infectionExposureRepository.save(e));
    }

    // ── Cognition / mood ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCognitionMood(String cpid) {
        return cognitionMoodScreenRepository
                .findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(ctx().tenantId(), cpid)
                .stream().map(this::mapCognition).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> recordCognitionMood(Map<String, Object> body) {
        TrustContext c = ctx();
        CognitionMoodScreenEntity e = new CognitionMoodScreenEntity();
        e.setTenantId(c.tenantId());
        e.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        e.setJourneyId(str(body.get("journey_id"), body.get("journeyId")));
        e.setEncounterId(str(body.get("encounter_id"), body.get("encounterId")));
        accessGuard.requireCareRelationship(c, e.getSubjectCpid(), e.getJourneyId(), e.getEncounterId());
        e.setInstrument(upper(required(body, "instrument", "instrument")));
        String score = str(body.get("score"));
        String absent = str(body.get("score_absent_reason"), body.get("scoreAbsentReason"));
        if (score != null && absent != null) {
            throw new IllegalArgumentException(
                    "Record a score or a reason it could not be scored — not both.");
        }
        if (score != null) e.setScore(new BigDecimal(score));
        e.setScoreAbsentReason(absent);
        e.setMoodNote(str(body.get("mood_note"), body.get("moodNote")));
        e.setRecordedBy(c.actorId());
        return mapCognition(cognitionMoodScreenRepository.save(e));
    }

    // ── Genetic risk ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listGeneticRisks(String cpid) {
        return geneticRiskRepository
                .findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(ctx().tenantId(), cpid)
                .stream().map(this::mapGenetic).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> recordGeneticRisk(Map<String, Object> body) {
        TrustContext c = ctx();
        GeneticRiskEntity e = new GeneticRiskEntity();
        e.setTenantId(c.tenantId());
        e.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        accessGuard.requireCareRelationship(c, e.getSubjectCpid(), null, null);
        e.setRiskFactor(required(body, "risk_factor", "riskFactor"));
        e.setDetail(str(body.get("detail")));
        e.setRecordedBy(c.actorId());
        return mapGenetic(geneticRiskRepository.save(e));
    }

    // ── Safeguarding (confidential) ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSafeguarding(String cpid) {
        return safeguardingDisclosureRepository
                .findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(ctx().tenantId(), cpid)
                .stream().map(this::mapSafeguarding).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> recordSafeguarding(Map<String, Object> body) {
        TrustContext c = ctx();
        SafeguardingDisclosureEntity e = new SafeguardingDisclosureEntity();
        e.setTenantId(c.tenantId());
        e.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        e.setJourneyId(str(body.get("journey_id"), body.get("journeyId")));
        e.setEncounterId(str(body.get("encounter_id"), body.get("encounterId")));
        accessGuard.requireCareRelationship(c, e.getSubjectCpid(), e.getJourneyId(), e.getEncounterId());
        e.setDisclosureSummary(required(body, "disclosure_summary", "disclosureSummary"));
        e.setConfidentialityLane("SAFEGUARDING");
        e.setRecordedBy(c.actorId());
        return mapSafeguarding(safeguardingDisclosureRepository.save(e));
    }

    // ── maps / helpers ────────────────────────────────────────────────────────

    private Map<String, Object> mapPresenting(PresentingConcernEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("concern_id", e.getConcernId().toString());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("journey_id", e.getJourneyId());
        m.put("encounter_id", e.getEncounterId());
        m.put("concern_text", e.getConcernText());
        m.put("onset_at", e.getOnsetAt() == null ? null : e.getOnsetAt().toString());
        m.put("timeline_json", e.getTimelineJson());
        m.put("source", e.getSource());
        m.put("recorded_by", e.getRecordedBy());
        m.put("created_at", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> mapSystems(SystemsReviewEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("review_id", e.getReviewId().toString());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("journey_id", e.getJourneyId());
        m.put("encounter_id", e.getEncounterId());
        m.put("systems_json", e.getSystemsJson());
        m.put("recorded_by", e.getRecordedBy());
        m.put("created_at", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> mapPriorIcu(PriorIcuHistoryEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entry_id", e.getEntryId().toString());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("approx_year", e.getApproxYear());
        m.put("facility_note", e.getFacilityNote());
        m.put("reason", e.getReason());
        m.put("recorded_by", e.getRecordedBy());
        m.put("created_at", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> mapExposure(InfectionExposureEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exposure_id", e.getExposureId().toString());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("journey_id", e.getJourneyId());
        m.put("encounter_id", e.getEncounterId());
        m.put("exposure_type", e.getExposureType());
        m.put("detail", e.getDetail());
        m.put("exposed_on", e.getExposedOn() == null ? null : e.getExposedOn().toString());
        m.put("recorded_by", e.getRecordedBy());
        m.put("created_at", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> mapCognition(CognitionMoodScreenEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("screen_id", e.getScreenId().toString());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("journey_id", e.getJourneyId());
        m.put("encounter_id", e.getEncounterId());
        m.put("instrument", e.getInstrument());
        m.put("score", e.getScore());
        m.put("score_absent_reason", e.getScoreAbsentReason());
        m.put("mood_note", e.getMoodNote());
        m.put("recorded_by", e.getRecordedBy());
        m.put("created_at", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> mapGenetic(GeneticRiskEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("risk_id", e.getRiskId().toString());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("risk_factor", e.getRiskFactor());
        m.put("detail", e.getDetail());
        m.put("recorded_by", e.getRecordedBy());
        m.put("created_at", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> mapSafeguarding(SafeguardingDisclosureEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("disclosure_id", e.getDisclosureId().toString());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("journey_id", e.getJourneyId());
        m.put("encounter_id", e.getEncounterId());
        m.put("disclosure_summary", e.getDisclosureSummary());
        m.put("confidentiality_lane", e.getConfidentialityLane());
        m.put("recorded_by", e.getRecordedBy());
        m.put("created_at", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        return m;
    }

    private static void requireAnchor(String journeyId, String encounterId) {
        if (journeyId == null && encounterId == null) {
            throw new IllegalArgumentException("journey_id or encounter_id is required (CC-5).");
        }
    }

    private static String required(Map<String, Object> body, String snake, String camel) {
        String v = str(body.get(snake), body.get(camel));
        if (v == null) throw new IllegalArgumentException("Missing field: " + snake);
        return v;
    }

    private static String oneOf(String field, String value, Set<String> allowed) {
        String n = value.toUpperCase(Locale.ROOT);
        if (!allowed.contains(n)) {
            throw new IllegalArgumentException("Unrecognised " + field + ": " + value);
        }
        return n;
    }

    private static String str(Object... candidates) {
        for (Object v : candidates) {
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) return s;
        }
        return null;
    }

    private static String upper(String v) {
        return v == null ? null : v.toUpperCase(Locale.ROOT);
    }
}
