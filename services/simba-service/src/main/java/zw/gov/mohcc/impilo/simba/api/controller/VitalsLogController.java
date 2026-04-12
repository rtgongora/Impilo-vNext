package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.persistence.entity.VitalsLogEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.VitalsLogRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/wellness/vitals")
public class VitalsLogController {

    private final VitalsLogRepository vitalsLogRepository;

    public VitalsLogController(VitalsLogRepository vitalsLogRepository) {
        this.vitalsLogRepository = vitalsLogRepository;
    }

    @PostMapping
    public ResponseEntity<VitalsLogEntity> post(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        VitalsLogEntity e = new VitalsLogEntity();
        e.setTenantId(tenantId);
        e.setPersonCpid(required(body, "person_cpid"));
        e.setVitalType(required(body, "vital_type"));
        if (body.get("value_numeric") != null) {
            e.setValueNumeric(new BigDecimal(body.get("value_numeric").toString()));
        }
        if (body.get("value_text") instanceof String vt) {
            e.setValueText(vt);
        }
        if (body.get("unit") instanceof String u) {
            e.setUnit(u);
        }
        if (body.get("source") instanceof String s) {
            e.setSource(s);
        }
        e.setRecordedAt(body.get("recorded_at") == null
                ? OffsetDateTime.now()
                : OffsetDateTime.parse(body.get("recorded_at").toString()));
        return ResponseEntity.status(HttpStatus.CREATED).body(vitalsLogRepository.save(e));
    }

    @GetMapping
    public List<VitalsLogEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam("person_cpid") String personCpid) {
        return vitalsLogRepository.findByTenantIdAndPersonCpidOrderByRecordedAtDesc(tenantId, personCpid);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }
}
