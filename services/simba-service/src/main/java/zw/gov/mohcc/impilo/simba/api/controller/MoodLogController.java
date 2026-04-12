package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.persistence.entity.MoodLogEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.MoodLogRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/wellness/mood")
public class MoodLogController {

    private final MoodLogRepository moodLogRepository;

    public MoodLogController(MoodLogRepository moodLogRepository) {
        this.moodLogRepository = moodLogRepository;
    }

    @PostMapping
    public ResponseEntity<MoodLogEntity> post(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        MoodLogEntity e = new MoodLogEntity();
        e.setTenantId(tenantId);
        e.setPersonCpid(required(body, "person_cpid"));
        e.setMoodScore(Integer.parseInt(body.get("mood_score").toString()));
        if (body.get("mood_label") instanceof String ml) {
            e.setMoodLabel(ml);
        }
        if (body.get("notes") instanceof String n) {
            e.setNotes(n);
        }
        e.setRecordedAt(body.get("recorded_at") == null
                ? OffsetDateTime.now()
                : OffsetDateTime.parse(body.get("recorded_at").toString()));
        return ResponseEntity.status(HttpStatus.CREATED).body(moodLogRepository.save(e));
    }

    @GetMapping
    public List<MoodLogEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam("person_cpid") String personCpid) {
        return moodLogRepository.findByTenantIdAndPersonCpidOrderByRecordedAtDesc(tenantId, personCpid);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }
}
