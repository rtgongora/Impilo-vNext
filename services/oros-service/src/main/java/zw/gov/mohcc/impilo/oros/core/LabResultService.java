package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.api.dto.ObservationInput;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultObservationEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.ResultObservationRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Records structured laboratory results (spec §7): authors a versioned report via
 * {@link ReportService} (preserving the never-overwrite-a-final discipline), persists the
 * per-analyte {@link ResultObservationEntity} rows, and — if any observation is flagged critical —
 * raises the result's critical workflow so the existing notification/escalation path fires.
 */
@Service
public class LabResultService {

    private static final Logger log = LoggerFactory.getLogger(LabResultService.class);

    private final ReportService reportService;
    private final ResultObservationRepository observationRepository;
    private final ObjectMapper objectMapper;

    public LabResultService(ReportService reportService,
                            ResultObservationRepository observationRepository,
                            ObjectMapper objectMapper) {
        this.reportService = reportService;
        this.observationRepository = observationRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Record a structured lab result for an order.
     *
     * @param isFinal {@code true} authors a FINAL report (validated/sign-off); {@code false} a PRELIMINARY one
     */
    @Transactional
    public ResultEntity recordLabResult(String orderId, boolean isFinal, String impression,
                                        String recommendations, String ziboResultCodes,
                                        List<ObservationInput> observations) {
        List<ObservationInput> obs = observations != null ? observations : List.of();
        String summaryJson = buildSummary(obs);

        ResultEntity result = isFinal
                ? reportService.createFinal(orderId, summaryJson, impression, recommendations, null, ziboResultCodes)
                : reportService.createPreliminary(orderId, summaryJson, impression, recommendations, null, ziboResultCodes);

        persistObservations(orderId, result.getResultId(), obs);

        // Per-analyte critical flag → raise the result-level critical workflow once.
        List<String> criticalAnalytes = obs.stream()
                .filter(o -> Boolean.TRUE.equals(o.criticalFlag()))
                .map(o -> o.analyteName() + (o.valueText() != null ? "=" + o.valueText()
                        : o.valueNumeric() != null ? "=" + o.valueNumeric() : ""))
                .collect(Collectors.toList());
        if (!criticalAnalytes.isEmpty()) {
            reportService.flagCritical(result.getResultId(), "Critical: " + String.join(", ", criticalAnalytes));
            log.info("Lab result flagged critical: orderId={}, resultId={}, analytes={}",
                    orderId, result.getResultId(), criticalAnalytes);
        }

        log.info("Lab result recorded: orderId={}, resultId={}, final={}, observations={}",
                orderId, result.getResultId(), isFinal, obs.size());
        return result;
    }

    /** Observations belonging to a result, in display order. */
    public List<ResultObservationEntity> observationsForResult(UUID resultId) {
        return observationRepository.findByResultIdOrderBySequenceAsc(resultId);
    }

    private void persistObservations(String orderId, UUID resultId, List<ObservationInput> obs) {
        int seq = 0;
        for (ObservationInput in : obs) {
            ResultObservationEntity e = new ResultObservationEntity();
            e.setResultId(resultId);
            e.setOrderId(orderId);
            e.setSequence(seq++);
            e.setAnalyteCode(in.analyteCode());
            e.setAnalyteSystem(in.analyteSystem());
            e.setAnalyteName(in.analyteName() != null ? in.analyteName() : "(unnamed)");
            e.setValueText(in.valueText());
            e.setValueNumeric(in.valueNumeric());
            e.setValueType(in.valueType());
            e.setUnit(in.unit());
            e.setRefRangeLow(in.refRangeLow());
            e.setRefRangeHigh(in.refRangeHigh());
            e.setRefRangeText(in.refRangeText());
            e.setAbnormalFlag(in.abnormalFlag());
            e.setCriticalFlag(Boolean.TRUE.equals(in.criticalFlag()));
            e.setComment(in.comment());
            observationRepository.save(e);
        }
    }

    /** Compact JSON snapshot of the observations, stored on the result for SHR writeback/back-compat. */
    private String buildSummary(List<ObservationInput> obs) {
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (ObservationInput o : obs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("analyte", o.analyteName());
                m.put("code", o.analyteCode());
                m.put("value", o.valueText() != null ? o.valueText() : o.valueNumeric());
                m.put("unit", o.unit());
                m.put("refRange", o.refRangeText());
                m.put("abnormalFlag", o.abnormalFlag());
                m.put("critical", Boolean.TRUE.equals(o.criticalFlag()));
                rows.add(m);
            }
            return objectMapper.writeValueAsString(Map.of("observations", rows));
        } catch (Exception e) {
            log.warn("Failed to build lab result summary snapshot: {}", e.getMessage());
            return "{}";
        }
    }
}
