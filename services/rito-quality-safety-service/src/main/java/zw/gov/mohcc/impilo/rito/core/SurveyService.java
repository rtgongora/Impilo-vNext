package zw.gov.mohcc.impilo.rito.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.events.RitoEventEmitter;
import zw.gov.mohcc.impilo.rito.persistence.entity.SurveyEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.SurveyResponseEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.SurveyRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.SurveyResponseRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Owning service for the Rito client-voice survey aggregate ({@code rit_survey} +
 * {@code rit_survey_response}). Captures CSAT / NPS feedback, scores it honestly from the
 * submitted answers, and — when satisfaction is low — opens a follow-up case so the signal is
 * never silently lost. Aggregations are computed from stored responses; empty surveys report
 * real zeros, never fabricated numbers.
 */
@Service
public class SurveyService {

    private static final BigDecimal CSAT_LOW_THRESHOLD = new BigDecimal("2");

    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final RitoEventEmitter emitter;
    private final CaseService caseService;

    public SurveyService(SurveyRepository surveyRepository,
                         SurveyResponseRepository surveyResponseRepository,
                         RitoEventEmitter emitter, CaseService caseService) {
        this.surveyRepository = surveyRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.emitter = emitter;
        this.caseService = caseService;
    }

    @Transactional
    public SurveyEntity createSurvey(UUID tenantId, String surveyKey, String name, String description,
                                     String surveyType, String formSchemaRef, List<Object> questions,
                                     Boolean anonymousAllowed) {
        String key = require(surveyKey, "surveyKey");
        if (surveyRepository.existsByTenantIdAndSurveyKey(tenantId, key)) {
            throw new IllegalArgumentException("Survey already exists for key: " + key);
        }
        SurveyEntity s = new SurveyEntity();
        s.setTenantId(tenantId);
        s.setSurveyKey(key);
        s.setName(require(name, "name"));
        s.setDescription(description);
        s.setSurveyType(surveyType != null ? surveyType : "CSAT");
        s.setFormSchemaRef(formSchemaRef);
        s.setQuestionsJson(JsonUtil.toJson(Map.of("questions", questions == null ? List.of() : questions)));
        s.setAnonymousAllowed(anonymousAllowed != null ? anonymousAllowed : Boolean.TRUE);
        s.setActive(Boolean.TRUE);
        return surveyRepository.save(s);
    }

    @Transactional(readOnly = true)
    public List<SurveyEntity> listSurveys(UUID tenantId) {
        return surveyRepository.findByTenantIdAndActiveTrue(tenantId);
    }

    @Transactional(readOnly = true)
    public SurveyEntity getSurvey(UUID tenantId, UUID id) {
        return surveyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Survey not found: " + id));
    }

    @Transactional
    public SurveyResponseEntity submitResponse(UUID tenantId, UUID surveyId, String respondentActorId,
                                               String respondentActorType, Boolean anonymous, UUID facilityId,
                                               String visitRef, Map<String, Object> answers, String freeText) {
        SurveyEntity survey = getSurvey(tenantId, surveyId);
        boolean isAnonymous = Boolean.TRUE.equals(anonymous);

        SurveyResponseEntity r = new SurveyResponseEntity();
        r.setSurveyId(survey.getId());
        r.setTenantId(tenantId);
        r.setAnonymous(isAnonymous);
        if (!isAnonymous) {
            r.setRespondentActorId(respondentActorId);
            r.setRespondentActorType(respondentActorType);
        }
        r.setFacilityId(facilityId);
        r.setVisitRef(visitRef);
        r.setAnswersJson(JsonUtil.toJson(answers));
        r.setFreeText(freeText);

        BigDecimal score = readScore(answers);
        r.setScore(score);

        String surveyType = survey.getSurveyType();
        String npsCategory = null;
        if ("NPS".equals(surveyType) && score != null) {
            int s = score.intValue();
            npsCategory = s >= 9 ? "PROMOTER" : s >= 7 ? "PASSIVE" : "DETRACTOR";
        }
        r.setNpsCategory(npsCategory);
        r.setSubmittedAt(OffsetDateTime.now());
        surveyResponseRepository.save(r);

        Map<String, Object> payload = new HashMap<>();
        payload.put("surveyId", survey.getId().toString());
        payload.put("surveyKey", survey.getSurveyKey());
        payload.put("surveyType", surveyType);
        payload.put("score", score != null ? score : null);
        payload.put("npsCategory", npsCategory);
        emitter.emit("SURVEY", survey.getId().toString(), "rito.feedback.submitted",
                "SURVEY_RESPONSE", r.getId().toString(), payload, tenantId);

        boolean lowCsat = "CSAT".equals(surveyType) && score != null
                && score.compareTo(CSAT_LOW_THRESHOLD) <= 0;
        boolean detractor = "NPS".equals(surveyType) && "DETRACTOR".equals(npsCategory);
        if (lowCsat || detractor) {
            CaseService.NewCase cmd = new CaseService.NewCase(
                    "SATISFACTION_SURVEY",
                    "Low satisfaction follow-up",
                    freeText,
                    "MODERATE",
                    "SYSTEM_SIGNAL",
                    null,
                    isAnonymous,
                    facilityId,
                    null,
                    null,
                    null,
                    respondentActorId,
                    respondentActorType,
                    null,
                    false,
                    Map.of("surveyId", surveyId.toString(), "responseScore", String.valueOf(score)));
            var followUp = caseService.createCase(tenantId, cmd);
            r.setCaseId(followUp.getId());
            surveyResponseRepository.save(r);
        }
        return r;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> aggregate(UUID tenantId, UUID surveyId) {
        SurveyEntity survey = getSurvey(tenantId, surveyId);
        List<SurveyResponseEntity> responses = surveyResponseRepository.findBySurveyId(survey.getId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("surveyKey", survey.getSurveyKey());
        out.put("surveyType", survey.getSurveyType());

        int total = responses.size();
        out.put("totalResponses", total);

        if (total == 0) {
            out.put("averageScore", null);
            if ("NPS".equals(survey.getSurveyType())) {
                out.put("npsScore", null);
                out.put("promoters", 0L);
                out.put("passives", 0L);
                out.put("detractors", 0L);
            }
            return out;
        }

        BigDecimal sum = BigDecimal.ZERO;
        long scored = 0;
        long promoters = 0;
        long passives = 0;
        long detractors = 0;
        for (SurveyResponseEntity r : responses) {
            if (r.getScore() != null) {
                sum = sum.add(r.getScore());
                scored++;
            }
            if ("PROMOTER".equals(r.getNpsCategory())) {
                promoters++;
            } else if ("PASSIVE".equals(r.getNpsCategory())) {
                passives++;
            } else if ("DETRACTOR".equals(r.getNpsCategory())) {
                detractors++;
            }
        }
        BigDecimal averageScore = scored > 0
                ? sum.divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP)
                : null;
        out.put("averageScore", averageScore);

        if ("NPS".equals(survey.getSurveyType())) {
            double promoterPct = (promoters * 100.0) / total;
            double detractorPct = (detractors * 100.0) / total;
            long npsScore = Math.round(promoterPct - detractorPct);
            out.put("npsScore", npsScore);
            out.put("promoters", promoters);
            out.put("passives", passives);
            out.put("detractors", detractors);
        }
        return out;
    }

    // ---- internal helpers ----

    private static BigDecimal readScore(Map<String, Object> answers) {
        if (answers == null) {
            return null;
        }
        Object raw = answers.get("score");
        if (raw == null) {
            raw = answers.get("rating");
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return null;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }
}
