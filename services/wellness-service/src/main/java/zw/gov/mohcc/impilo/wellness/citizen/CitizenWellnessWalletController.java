package zw.gov.mohcc.impilo.wellness.citizen;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Citizen wellness + health wallet — moved from experience-bff {@code CitizenMyLifeController}
 * so the domain owns its persistence (Phase F backend completeness).
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen")
public class CitizenWellnessWalletController {

    private final JdbcTemplate jdbc;

    public CitizenWellnessWalletController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/wellness/activities")
    public ResponseEntity<Map<String, Object>> getActivities(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId,
            @RequestParam(defaultValue = "7") int days) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM wellness_activities WHERE tenant_id = ? AND patient_id = ? AND activity_date >= CURRENT_DATE - ?::int ORDER BY activity_date DESC",
                tenantId, patientId, days);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/wellness/activities")
    @Transactional
    public ResponseEntity<Map<String, Object>> logActivity(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        jdbc.update(
                """
                INSERT INTO wellness_activities (tenant_id, patient_id, activity_date, steps, calories_burned, active_minutes, distance_km, sleep_hours, sleep_quality, water_ml)
                VALUES (?, ?, COALESCE(?::date, CURRENT_DATE), ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, patient_id, activity_date) DO UPDATE SET
                    steps = EXCLUDED.steps, calories_burned = EXCLUDED.calories_burned,
                    active_minutes = EXCLUDED.active_minutes, distance_km = EXCLUDED.distance_km,
                    sleep_hours = EXCLUDED.sleep_hours, sleep_quality = EXCLUDED.sleep_quality,
                    water_ml = EXCLUDED.water_ml
                """,
                tenantId,
                body.get("patientId"),
                body.getOrDefault("date", null),
                toInt(body.getOrDefault("steps", 0)),
                toInt(body.getOrDefault("caloriesBurned", 0)),
                toInt(body.getOrDefault("activeMinutes", 0)),
                toBigDecimal(body.getOrDefault("distanceKm", 0)),
                toBigDecimal(body.getOrDefault("sleepHours", null)),
                body.getOrDefault("sleepQuality", null),
                toInt(body.getOrDefault("waterMl", 0)));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/wellness/vitals")
    public ResponseEntity<Map<String, Object>> getVitals(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId,
            @RequestParam(required = false) String type) {
        String sql = type != null
                ? "SELECT * FROM wellness_vitals_log WHERE tenant_id = ? AND patient_id = ? AND vital_type = ? ORDER BY measured_at DESC LIMIT 50"
                : "SELECT * FROM wellness_vitals_log WHERE tenant_id = ? AND patient_id = ? ORDER BY measured_at DESC LIMIT 50";
        List<Map<String, Object>> rows =
                type != null ? jdbc.queryForList(sql, tenantId, patientId, type) : jdbc.queryForList(sql, tenantId, patientId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/wellness/vitals")
    @Transactional
    public ResponseEntity<Map<String, Object>> logVital(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO wellness_vitals_log (id, tenant_id, patient_id, vital_type, value, unit, source, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                tenantId,
                body.get("patientId"),
                body.get("vitalType"),
                toBigDecimal(body.get("value")),
                body.get("unit"),
                body.getOrDefault("source", "MANUAL"),
                body.getOrDefault("notes", ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @GetMapping("/wellness/mood")
    public ResponseEntity<Map<String, Object>> getMoodLog(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM wellness_mood_log WHERE tenant_id = ? AND patient_id = ? ORDER BY logged_at DESC LIMIT 30",
                tenantId,
                patientId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/wellness/mood")
    @Transactional
    public ResponseEntity<Map<String, Object>> logMood(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO wellness_mood_log (id, tenant_id, patient_id, mood_score, energy_level, stress_level, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                tenantId,
                body.get("patientId"),
                toInt(body.get("moodScore")),
                toInt(body.getOrDefault("energyLevel", null)),
                toInt(body.getOrDefault("stressLevel", null)),
                body.getOrDefault("notes", ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @GetMapping("/wellness/challenges")
    public ResponseEntity<Map<String, Object>> getChallenges(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM wellness_challenges WHERE tenant_id = ? AND status = 'ACTIVE' ORDER BY start_date DESC",
                tenantId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/wellness/challenges/{id}/join")
    @Transactional
    public ResponseEntity<Map<String, Object>> joinChallenge(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update(
                """
                INSERT INTO wellness_challenge_participants (challenge_id, patient_id) VALUES (?, ?)
                ON CONFLICT (challenge_id, patient_id) DO NOTHING
                """,
                id,
                body.get("patientId"));
        jdbc.update("UPDATE wellness_challenges SET participant_count = participant_count + 1 WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("joined", true));
    }

    @GetMapping("/wallet")
    public ResponseEntity<Map<String, Object>> getWallet(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM health_wallets WHERE tenant_id = ? AND patient_id = ?", tenantId, patientId);
        if (rows.isEmpty()) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO health_wallets (id, tenant_id, patient_id) VALUES (?, ?, ?)", id, tenantId, patientId);
            return ResponseEntity.ok(Map.of("data", Map.of("id", id, "balance", 0, "currency", "ZWL")));
        }
        return ResponseEntity.ok(Map.of("data", rows.get(0)));
    }

    @GetMapping("/wallet/transactions")
    public ResponseEntity<Map<String, Object>> getTransactions(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT t.* FROM wallet_transactions t
                JOIN health_wallets w ON t.wallet_id = w.id
                WHERE w.tenant_id = ? AND w.patient_id = ?
                ORDER BY t.created_at DESC LIMIT 50
                """,
                tenantId,
                patientId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    private static int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(v.toString());
    }
}
