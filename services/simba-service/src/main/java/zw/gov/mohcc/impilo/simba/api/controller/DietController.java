package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.DietService;
import zw.gov.mohcc.impilo.simba.persistence.entity.DietEntryEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/wellness/diet")
public class DietController {

    private final DietService dietService;

    public DietController(DietService dietService) {
        this.dietService = dietService;
    }

    @PostMapping
    public ResponseEntity<DietEntryEntity> post(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        DietEntryEntity e = new DietEntryEntity();
        e.setTenantId(tenantId);
        e.setPersonCpid(required(body, "person_cpid"));
        e.setMealType(required(body, "meal_type"));
        if (body.get("description") instanceof String d) {
            e.setDescription(d);
        }
        if (body.get("calories") != null) {
            e.setCalories(Integer.parseInt(body.get("calories").toString()));
        }
        e.setProteinG(decimalOrNull(body.get("protein_g")));
        e.setCarbsG(decimalOrNull(body.get("carbs_g")));
        e.setFatG(decimalOrNull(body.get("fat_g")));
        e.setFiberG(decimalOrNull(body.get("fiber_g")));
        if (body.get("water_ml") != null) {
            e.setWaterMl(Integer.parseInt(body.get("water_ml").toString()));
        }
        e.setRecordedAt(body.get("recorded_at") == null
                ? OffsetDateTime.now()
                : OffsetDateTime.parse(body.get("recorded_at").toString()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dietService.record(e));
    }

    @GetMapping
    public List<DietEntryEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam("person_cpid") String personCpid) {
        return dietService.list(tenantId, personCpid);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static BigDecimal decimalOrNull(Object v) {
        if (v == null) {
            return null;
        }
        return new BigDecimal(v.toString());
    }
}
