package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.persistence.entity.ExerciseSessionEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.ExerciseSessionRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/wellness/exercise")
public class ExerciseController {

    private final ExerciseSessionRepository exerciseSessionRepository;

    public ExerciseController(ExerciseSessionRepository exerciseSessionRepository) {
        this.exerciseSessionRepository = exerciseSessionRepository;
    }

    @PostMapping
    public ResponseEntity<ExerciseSessionEntity> post(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        ExerciseSessionEntity e = new ExerciseSessionEntity();
        e.setTenantId(tenantId);
        e.setPersonCpid(required(body, "person_cpid"));
        e.setExerciseType(required(body, "exercise_type"));
        e.setStartTime(OffsetDateTime.parse(body.get("start_time").toString()));
        if (body.get("end_time") != null) {
            e.setEndTime(OffsetDateTime.parse(body.get("end_time").toString()));
        }
        if (body.get("calories") != null) {
            e.setCalories(Integer.parseInt(body.get("calories").toString()));
        }
        if (body.get("distance_meters") != null) {
            e.setDistanceMeters(Integer.parseInt(body.get("distance_meters").toString()));
        }
        if (body.get("laps") != null) {
            e.setLaps(Integer.parseInt(body.get("laps").toString()));
        }
        if (body.get("repetitions") != null) {
            e.setRepetitions(Integer.parseInt(body.get("repetitions").toString()));
        }
        if (body.get("source") instanceof String s) {
            e.setSource(s);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(exerciseSessionRepository.save(e));
    }

    @GetMapping
    public List<ExerciseSessionEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam("person_cpid") String personCpid) {
        return exerciseSessionRepository.findByTenantIdAndPersonCpidOrderByStartTimeDesc(tenantId, personCpid);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }
}
