package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.ChallengeService;
import zw.gov.mohcc.impilo.simba.persistence.entity.ChallengeEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.ChallengeParticipantEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/wellness/challenges")
public class ChallengeController {

    private final ChallengeService challengeService;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @PostMapping
    public ResponseEntity<ChallengeEntity> post(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        ChallengeEntity c = new ChallengeEntity();
        c.setTenantId(tenantId);
        c.setTitle(required(body, "title"));
        if (body.get("description") instanceof String d) {
            c.setDescription(d);
        }
        c.setChallengeType(required(body, "challenge_type"));
        if (body.get("target_value") != null) {
            c.setTargetValue(new BigDecimal(body.get("target_value").toString()));
        }
        if (body.get("unit") instanceof String u) {
            c.setUnit(u);
        }
        c.setStartDate(LocalDate.parse(body.get("start_date").toString()));
        c.setEndDate(LocalDate.parse(body.get("end_date").toString()));
        if (body.get("status") instanceof String s) {
            c.setStatus(s);
        }
        if (body.get("created_by") instanceof String cb) {
            c.setCreatedBy(cb);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(challengeService.create(c));
    }

    @GetMapping
    public List<ChallengeEntity> list(@RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return challengeService.list(tenantId);
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ChallengeParticipantEntity> join(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("id") UUID challengeId,
            @RequestBody Map<String, Object> body) {
        String personCpid = required(body, "person_cpid");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(challengeService.join(challengeId, tenantId, personCpid));
    }

    @PutMapping("/{id}/progress")
    public ChallengeParticipantEntity progress(
            @PathVariable("id") UUID challengeId,
            @RequestBody Map<String, Object> body) {
        String personCpid = required(body, "person_cpid");
        if (body.get("progress_value") == null) {
            throw new IllegalArgumentException("Missing progress_value");
        }
        BigDecimal pv = new BigDecimal(body.get("progress_value").toString());
        return challengeService.updateProgress(challengeId, personCpid, pv);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }
}
