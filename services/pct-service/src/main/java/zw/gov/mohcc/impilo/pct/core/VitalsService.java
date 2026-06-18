package zw.gov.mohcc.impilo.pct.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.VitalsEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.VitalsRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class VitalsService {

    private static final Logger log = LoggerFactory.getLogger(VitalsService.class);

    private final VitalsRepository repository;

    public VitalsService(VitalsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<VitalsEntity> listByPatient(UUID tenantId, String patientId, int page, int size) {
        return repository.findByTenantIdAndPatientIdOrderByRecordedAtDesc(
                tenantId, patientId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<VitalsEntity> listByEncounter(UUID tenantId, String encounterId, int page, int size) {
        return repository.findByTenantIdAndEncounterIdOrderByRecordedAtDesc(
                tenantId, encounterId, PageRequest.of(page, size));
    }

    @Transactional
    public VitalsEntity create(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();

        VitalsEntity row = new VitalsEntity();
        row.setTenantId(ctx.tenantId());
        row.setPatientId(requiredString(body, "patient_id"));
        row.setEncounterId(stringOr(body.get("encounter_id"), null));
        row.setRecordedBy(stringOr(body.get("recorded_by"), ctx.actorId()));
        row.setSystolic(toInt(body.get("systolic")));
        row.setDiastolic(toInt(body.get("diastolic")));
        row.setHeartRate(toInt(body.get("heart_rate")));
        row.setTemperature(toBigDecimal(body.get("temperature")));
        row.setRespiratoryRate(toInt(body.get("respiratory_rate")));
        row.setOxygenSaturation(toBigDecimal(body.get("oxygen_saturation")));
        row.setWeight(toBigDecimal(body.get("weight")));
        row.setHeight(toBigDecimal(body.get("height")));
        row.setPainScore(toInt(body.get("pain_score")));
        row.setNotes(stringOr(body.get("notes"), null));

        row = repository.save(row);
        log.info("pct.vitals.created id={} patientId={} correlationId={}",
                row.getId(), row.getPatientId(), ctx.correlationId());
        return row;
    }

    public Map<String, Object> toMap(VitalsEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("patient_id", e.getPatientId());
        m.put("encounter_id", e.getEncounterId());
        m.put("recorded_by", e.getRecordedBy());
        m.put("systolic", e.getSystolic());
        m.put("diastolic", e.getDiastolic());
        m.put("heart_rate", e.getHeartRate());
        m.put("temperature", e.getTemperature());
        m.put("respiratory_rate", e.getRespiratoryRate());
        m.put("oxygen_saturation", e.getOxygenSaturation());
        m.put("weight", e.getWeight());
        m.put("height", e.getHeight());
        m.put("pain_score", e.getPainScore());
        m.put("notes", e.getNotes());
        String ts = e.getRecordedAt() != null ? e.getRecordedAt().toString() : null;
        m.put("recorded_at", ts);
        m.put("created_at", ts);
        return m;
    }

    // ── Coercion helpers (Jackson may deliver numbers as Integer/Double/String) ──

    private static String requiredString(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return String.valueOf(v).trim();
    }

    private static String stringOr(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
