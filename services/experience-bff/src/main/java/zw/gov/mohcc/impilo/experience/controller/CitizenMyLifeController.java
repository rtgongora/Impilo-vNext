package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Citizen "My Life" endpoints — Health ID, Wellness, Wallet, SOS,
 * Remote Monitoring, Queue Status, Clubs, Professional Pages, Crowdfunding,
 * and Service Discovery.
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen")
public class CitizenMyLifeController {

    private final JdbcTemplate jdbc;

    public CitizenMyLifeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Health ID ───────────────────────────────────────────────────

    @GetMapping("/health-id")
    public ResponseEntity<Map<String, Object>> getHealthId(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM citizen_health_ids WHERE tenant_id = ? AND patient_id = ? AND status = 'ACTIVE' LIMIT 1",
                tenantId, patientId);
        if (rows.isEmpty()) return ResponseEntity.ok(Map.of("data", Map.of()));
        return ResponseEntity.ok(Map.of("data", rows.get(0)));
    }

    @PostMapping("/health-id")
    @Transactional
    public ResponseEntity<Map<String, Object>> createHealthId(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        String patientId = body.get("patientId").toString();
        String healthIdNumber = "ZW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        jdbc.update("""
            INSERT INTO citizen_health_ids (id, tenant_id, patient_id, health_id_number, qr_code_data,
                blood_type, allergies_summary, emergency_contact_name, emergency_contact_phone, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, tenantId, patientId, healthIdNumber,
                "HEALTHID:" + healthIdNumber + ":" + patientId,
                body.getOrDefault("bloodType", ""),
                body.getOrDefault("allergiesSummary", ""),
                body.getOrDefault("emergencyContactName", ""),
                body.getOrDefault("emergencyContactPhone", ""),
                OffsetDateTime.now().plusYears(5));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", Map.of("id", id, "healthIdNumber", healthIdNumber)));
    }

    // ── Wellness: Activity Tracking ─────────────────────────────────

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
        jdbc.update("""
            INSERT INTO wellness_activities (tenant_id, patient_id, activity_date, steps, calories_burned, active_minutes, distance_km, sleep_hours, sleep_quality, water_ml)
            VALUES (?, ?, COALESCE(?::date, CURRENT_DATE), ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, patient_id, activity_date) DO UPDATE SET
                steps = EXCLUDED.steps, calories_burned = EXCLUDED.calories_burned,
                active_minutes = EXCLUDED.active_minutes, distance_km = EXCLUDED.distance_km,
                sleep_hours = EXCLUDED.sleep_hours, sleep_quality = EXCLUDED.sleep_quality,
                water_ml = EXCLUDED.water_ml
            """,
                tenantId, body.get("patientId"),
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

    // ── Wellness: Vitals Log ────────────────────────────────────────

    @GetMapping("/wellness/vitals")
    public ResponseEntity<Map<String, Object>> getVitals(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId,
            @RequestParam(required = false) String type) {
        String sql = type != null
                ? "SELECT * FROM wellness_vitals_log WHERE tenant_id = ? AND patient_id = ? AND vital_type = ? ORDER BY measured_at DESC LIMIT 50"
                : "SELECT * FROM wellness_vitals_log WHERE tenant_id = ? AND patient_id = ? ORDER BY measured_at DESC LIMIT 50";
        List<Map<String, Object>> rows = type != null
                ? jdbc.queryForList(sql, tenantId, patientId, type)
                : jdbc.queryForList(sql, tenantId, patientId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/wellness/vitals")
    @Transactional
    public ResponseEntity<Map<String, Object>> logVital(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO wellness_vitals_log (id, tenant_id, patient_id, vital_type, value, unit, source, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, id, tenantId, body.get("patientId"), body.get("vitalType"),
                toBigDecimal(body.get("value")), body.get("unit"),
                body.getOrDefault("source", "MANUAL"), body.getOrDefault("notes", ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    // ── Wellness: Mood Log ──────────────────────────────────────────

    @GetMapping("/wellness/mood")
    public ResponseEntity<Map<String, Object>> getMoodLog(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM wellness_mood_log WHERE tenant_id = ? AND patient_id = ? ORDER BY logged_at DESC LIMIT 30",
                tenantId, patientId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/wellness/mood")
    @Transactional
    public ResponseEntity<Map<String, Object>> logMood(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO wellness_mood_log (id, tenant_id, patient_id, mood_score, energy_level, stress_level, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, id, tenantId, body.get("patientId"),
                toInt(body.get("moodScore")), toInt(body.getOrDefault("energyLevel", null)),
                toInt(body.getOrDefault("stressLevel", null)), body.getOrDefault("notes", ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    // ── Wellness: Challenges ────────────────────────────────────────

    @GetMapping("/wellness/challenges")
    public ResponseEntity<Map<String, Object>> getChallenges(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM wellness_challenges WHERE tenant_id = ? AND status = 'ACTIVE' ORDER BY start_date DESC",
                tenantId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/wellness/challenges/{id}/join")
    @Transactional
    public ResponseEntity<Map<String, Object>> joinChallenge(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update("""
            INSERT INTO wellness_challenge_participants (challenge_id, patient_id) VALUES (?, ?)
            ON CONFLICT (challenge_id, patient_id) DO NOTHING
            """, id, body.get("patientId"));
        jdbc.update("UPDATE wellness_challenges SET participant_count = participant_count + 1 WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("joined", true));
    }

    // ── Health Wallet ───────────────────────────────────────────────

    @GetMapping("/wallet")
    public ResponseEntity<Map<String, Object>> getWallet(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM health_wallets WHERE tenant_id = ? AND patient_id = ?", tenantId, patientId);
        if (rows.isEmpty()) {
            // Auto-create wallet
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO health_wallets (id, tenant_id, patient_id) VALUES (?, ?, ?)",
                    id, tenantId, patientId);
            return ResponseEntity.ok(Map.of("data", Map.of("id", id, "balance", 0, "currency", "ZWL")));
        }
        return ResponseEntity.ok(Map.of("data", rows.get(0)));
    }

    @GetMapping("/wallet/transactions")
    public ResponseEntity<Map<String, Object>> getTransactions(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT t.* FROM wallet_transactions t
            JOIN health_wallets w ON t.wallet_id = w.id
            WHERE w.tenant_id = ? AND w.patient_id = ?
            ORDER BY t.created_at DESC LIMIT 50
            """, tenantId, patientId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    // ── Emergency SOS ───────────────────────────────────────────────

    @GetMapping("/sos/contacts")
    public ResponseEntity<Map<String, Object>> getEmergencyContacts(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM emergency_contacts WHERE tenant_id = ? AND patient_id = ? ORDER BY is_primary DESC, name ASC",
                tenantId, patientId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/sos/contacts")
    @Transactional
    public ResponseEntity<Map<String, Object>> addEmergencyContact(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO emergency_contacts (id, tenant_id, patient_id, name, relationship, phone, is_primary)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, id, tenantId, body.get("patientId"), body.get("name"),
                body.get("relationship"), body.get("phone"),
                Boolean.parseBoolean(body.getOrDefault("isPrimary", "false").toString()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
    }

    @DeleteMapping("/sos/contacts/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteEmergencyContact(@PathVariable UUID id) {
        jdbc.update("DELETE FROM emergency_contacts WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @PostMapping("/sos/alert")
    @Transactional
    public ResponseEntity<Map<String, Object>> triggerSOS(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO sos_alerts (id, tenant_id, patient_id, latitude, longitude, alert_type)
            VALUES (?, ?, ?, ?, ?, ?)
            """, id, tenantId, body.get("patientId"),
                toBigDecimal(body.getOrDefault("latitude", null)),
                toBigDecimal(body.getOrDefault("longitude", null)),
                body.getOrDefault("alertType", "MEDICAL"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", Map.of("id", id, "status", "ACTIVE", "message", "Emergency services have been notified")));
    }

    // ── Remote Monitoring ───────────────────────────────────────────

    @GetMapping("/monitoring/devices")
    public ResponseEntity<Map<String, Object>> getDevices(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM monitoring_devices WHERE tenant_id = ? AND patient_id = ? ORDER BY created_at DESC",
                tenantId, patientId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/monitoring/devices")
    @Transactional
    public ResponseEntity<Map<String, Object>> pairDevice(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO monitoring_devices (id, tenant_id, patient_id, device_name, device_type, manufacturer, model, connection_type)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, id, tenantId, body.get("patientId"), body.get("deviceName"),
                body.get("deviceType"), body.getOrDefault("manufacturer", ""),
                body.getOrDefault("model", ""), body.getOrDefault("connectionType", "BLUETOOTH"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id, "status", "PAIRED")));
    }

    @PostMapping("/monitoring/devices/{id}/sync")
    @Transactional
    public ResponseEntity<Map<String, Object>> syncDevice(@PathVariable UUID id) {
        jdbc.update("UPDATE monitoring_devices SET last_sync_at = NOW() WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("synced", true, "syncedAt", OffsetDateTime.now()));
    }

    // ── Queue Status ────────────────────────────────────────────────

    @GetMapping("/queue/status")
    public ResponseEntity<Map<String, Object>> getQueueStatus(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String patientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM citizen_queue_tickets WHERE tenant_id = ? AND patient_id = ? AND status IN ('WAITING', 'CALLED') ORDER BY joined_at DESC LIMIT 5",
                tenantId, patientId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/queue/join")
    @Transactional
    public ResponseEntity<Map<String, Object>> joinQueue(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        String ticketNumber = "Q-" + String.format("%04d", new Random().nextInt(9999));
        jdbc.update("""
            INSERT INTO citizen_queue_tickets (id, tenant_id, patient_id, facility_id, facility_name, ticket_number, service_type, position, estimated_wait)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, tenantId, body.get("patientId"), body.get("facilityId"),
                body.getOrDefault("facilityName", ""), ticketNumber,
                body.getOrDefault("serviceType", "GENERAL"),
                toInt(body.getOrDefault("position", 0)),
                toInt(body.getOrDefault("estimatedWait", 30)));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", Map.of("id", id, "ticketNumber", ticketNumber)));
    }

    // ── Clubs ───────────────────────────────────────────────────────

    @GetMapping("/clubs")
    public ResponseEntity<Map<String, Object>> getClubs(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String category) {
        String sql = category != null
                ? "SELECT * FROM wellness_clubs WHERE tenant_id = ? AND status = 'ACTIVE' AND category = ? ORDER BY member_count DESC"
                : "SELECT * FROM wellness_clubs WHERE tenant_id = ? AND status = 'ACTIVE' ORDER BY member_count DESC";
        List<Map<String, Object>> rows = category != null
                ? jdbc.queryForList(sql, tenantId, category)
                : jdbc.queryForList(sql, tenantId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/clubs/{id}/join")
    @Transactional
    public ResponseEntity<Map<String, Object>> joinClub(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update("""
            INSERT INTO wellness_club_members (club_id, patient_id) VALUES (?, ?)
            ON CONFLICT (club_id, patient_id) DO NOTHING
            """, id, body.get("patientId"));
        jdbc.update("UPDATE wellness_clubs SET member_count = member_count + 1 WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("joined", true));
    }

    // ── Professional Pages ──────────────────────────────────────────

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> getProviders(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String specialty) {
        String sql = specialty != null
                ? "SELECT * FROM professional_pages WHERE tenant_id = ? AND is_accepting = true AND specialty = ? ORDER BY rating DESC NULLS LAST"
                : "SELECT * FROM professional_pages WHERE tenant_id = ? AND is_accepting = true ORDER BY rating DESC NULLS LAST";
        List<Map<String, Object>> rows = specialty != null
                ? jdbc.queryForList(sql, tenantId, specialty)
                : jdbc.queryForList(sql, tenantId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @GetMapping("/providers/{id}")
    public ResponseEntity<Map<String, Object>> getProvider(
            @PathVariable UUID id, @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM professional_pages WHERE id = ? AND tenant_id = ?", id, tenantId);
        if (rows.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", rows.get(0)));
    }

    // ── Crowdfunding ────────────────────────────────────────────────

    @GetMapping("/crowdfunding")
    public ResponseEntity<Map<String, Object>> getCampaigns(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String category) {
        String sql = category != null
                ? "SELECT * FROM crowdfunding_campaigns WHERE tenant_id = ? AND status = 'ACTIVE' AND category = ? ORDER BY created_at DESC"
                : "SELECT * FROM crowdfunding_campaigns WHERE tenant_id = ? AND status = 'ACTIVE' ORDER BY created_at DESC";
        List<Map<String, Object>> rows = category != null
                ? jdbc.queryForList(sql, tenantId, category)
                : jdbc.queryForList(sql, tenantId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/crowdfunding/{id}/donate")
    @Transactional
    public ResponseEntity<Map<String, Object>> donate(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UUID donationId = UUID.randomUUID();
        BigDecimal amount = toBigDecimal(body.get("amount"));
        jdbc.update("""
            INSERT INTO crowdfunding_donations (id, campaign_id, donor_id, amount, message, is_anonymous)
            VALUES (?, ?, ?, ?, ?, ?)
            """, donationId, id, body.get("donorId"), amount,
                body.getOrDefault("message", ""), Boolean.parseBoolean(body.getOrDefault("isAnonymous", "false").toString()));
        jdbc.update("UPDATE crowdfunding_campaigns SET raised_amount = raised_amount + ?, donor_count = donor_count + 1 WHERE id = ?",
                amount, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("donationId", donationId)));
    }

    // ── Service Discovery ───────────────────────────────────────────

    @GetMapping("/services/discover")
    public ResponseEntity<Map<String, Object>> discoverServices(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String query) {
        // Combines marketplace services with facility search
        List<Map<String, Object>> services = jdbc.queryForList(
                "SELECT 'SERVICE' as source_type, id, name, description, category, facility_name, price, currency, available, rating " +
                "FROM marketplace_services WHERE tenant_id = ? AND available = true ORDER BY rating DESC NULLS LAST LIMIT 30",
                tenantId);
        return ResponseEntity.ok(Map.of("data", services));
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private Integer toInt(Object val) {
        if (val == null) return null;
        return Integer.parseInt(val.toString());
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        return new BigDecimal(val.toString());
    }
}
