package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Communication Noticeboard — announcements, clinical pages, and messages.
 *
 * <p>Announcements: CRUD with categories (general, policy, training, emergency, shift),
 * priorities (low, normal, high, urgent), pinning, expiry, and acknowledgment tracking.</p>
 *
 * <p>Clinical Pages: urgent nurse/doctor paging with read/response tracking.</p>
 *
 * <p>Clinical Messages: secure messaging channels between staff.</p>
 */
@RestController
@RequestMapping("/internal/v1/communication")
public class CommunicationController {

    private final JdbcTemplate jdbc;

    public CommunicationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Announcements ───────────────────────────────────────────────

    @GetMapping("/announcements")
    public ResponseEntity<Map<String, Object>> listAnnouncements(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        String sql = category != null && !category.isEmpty()
                ? "SELECT * FROM announcements WHERE tenant_id = ? AND status = 'ACTIVE' AND (expires_at IS NULL OR expires_at > NOW()) AND category = ? ORDER BY is_pinned DESC, published_at DESC LIMIT ? OFFSET ?"
                : "SELECT * FROM announcements WHERE tenant_id = ? AND status = 'ACTIVE' AND (expires_at IS NULL OR expires_at > NOW()) ORDER BY is_pinned DESC, published_at DESC LIMIT ? OFFSET ?";

        List<Map<String, Object>> rows = category != null && !category.isEmpty()
                ? jdbc.queryForList(sql, tenantId, category, limit, offset)
                : jdbc.queryForList(sql, tenantId, limit, offset);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM announcements WHERE tenant_id = ? AND status = 'ACTIVE' AND (expires_at IS NULL OR expires_at > NOW())",
                Long.class, tenantId);

        Long pinnedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM announcements WHERE tenant_id = ? AND status = 'ACTIVE' AND is_pinned = true AND (expires_at IS NULL OR expires_at > NOW())",
                Long.class, tenantId);

        Long urgentCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM announcements WHERE tenant_id = ? AND status = 'ACTIVE' AND priority = 'urgent' AND (expires_at IS NULL OR expires_at > NOW())",
                Long.class, tenantId);

        return ResponseEntity.ok(Map.of(
                "data", rows,
                "stats", Map.of("total", total, "pinned", pinnedCount, "urgent", urgentCount)));
    }

    @GetMapping("/announcements/{id}")
    public ResponseEntity<Map<String, Object>> getAnnouncement(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM announcements WHERE id = ? AND tenant_id = ?", id, tenantId);
        if (rows.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", rows.get(0)));
    }

    @PostMapping("/announcements")
    @Transactional
    public ResponseEntity<Map<String, Object>> createAnnouncement(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {

        UUID id = UUID.randomUUID();
        String title = body.getOrDefault("title", "").toString();
        String content = body.getOrDefault("content", "").toString();
        String category = body.getOrDefault("category", "general").toString();
        String priority = body.getOrDefault("priority", "normal").toString();
        boolean isPinned = Boolean.parseBoolean(body.getOrDefault("isPinned", "false").toString());
        boolean requiresAck = Boolean.parseBoolean(body.getOrDefault("requiresAcknowledgment", "false").toString());
        String publishedBy = body.getOrDefault("publishedBy", "").toString();

        // Parse expiry
        OffsetDateTime expiresAt = null;
        if (body.containsKey("expiresInDays") && body.get("expiresInDays") != null) {
            int days = Integer.parseInt(body.get("expiresInDays").toString());
            if (days > 0) {
                expiresAt = OffsetDateTime.now().plusDays(days);
            }
        }

        jdbc.update("""
            INSERT INTO announcements (id, tenant_id, title, content, category, priority,
                is_pinned, requires_acknowledgment, published_by, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, tenantId, title, content, category, priority,
                isPinned, requiresAck, publishedBy, expiresAt);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", Map.of("id", id.toString(), "title", title, "category", category)));
    }

    @PostMapping("/announcements/{id}/acknowledge")
    @Transactional
    public ResponseEntity<Map<String, Object>> acknowledgeAnnouncement(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        String userId = body.getOrDefault("userId", "").toString();
        jdbc.update("""
            INSERT INTO announcement_acknowledgments (announcement_id, user_id)
            VALUES (?, ?) ON CONFLICT (announcement_id, user_id) DO NOTHING
            """, id, userId);
        return ResponseEntity.ok(Map.of("acknowledged", true));
    }

    @DeleteMapping("/announcements/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> archiveAnnouncement(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        jdbc.update("UPDATE announcements SET status = 'ARCHIVED', updated_at = NOW() WHERE id = ? AND tenant_id = ?",
                id, tenantId);
        return ResponseEntity.ok(Map.of("archived", true));
    }

    // ── Clinical Pages ──────────────────────────────────────────────

    @GetMapping("/pages")
    public ResponseEntity<Map<String, Object>> listPages(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String recipientId) {
        String sql = recipientId != null
                ? "SELECT * FROM clinical_pages WHERE tenant_id = ? AND recipient_id = ? ORDER BY sent_at DESC LIMIT 50"
                : "SELECT * FROM clinical_pages WHERE tenant_id = ? ORDER BY sent_at DESC LIMIT 50";
        List<Map<String, Object>> rows = recipientId != null
                ? jdbc.queryForList(sql, tenantId, recipientId)
                : jdbc.queryForList(sql, tenantId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/pages")
    @Transactional
    public ResponseEntity<Map<String, Object>> sendPage(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO clinical_pages (id, tenant_id, sender_id, sender_name, recipient_id, recipient_name, urgency, message, callback_number)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                id, tenantId,
                body.getOrDefault("senderId", ""), body.getOrDefault("senderName", ""),
                body.getOrDefault("recipientId", ""), body.getOrDefault("recipientName", ""),
                body.getOrDefault("urgency", "normal"),
                body.getOrDefault("message", ""),
                body.getOrDefault("callbackNumber", ""));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", Map.of("id", id.toString(), "status", "SENT")));
    }

    @PostMapping("/pages/{id}/read")
    @Transactional
    public ResponseEntity<Map<String, Object>> markPageRead(@PathVariable UUID id) {
        jdbc.update("UPDATE clinical_pages SET status = 'READ', read_at = NOW() WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("status", "READ"));
    }

    @PostMapping("/pages/{id}/respond")
    @Transactional
    public ResponseEntity<Map<String, Object>> respondToPage(@PathVariable UUID id) {
        jdbc.update("UPDATE clinical_pages SET status = 'RESPONDED', responded_at = NOW() WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("status", "RESPONDED"));
    }

    // ── Clinical Messages ───────────────────────────────────────────

    @GetMapping("/messages/channels")
    public ResponseEntity<Map<String, Object>> listChannels(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM message_channels WHERE tenant_id = ? ORDER BY last_message_at DESC NULLS LAST LIMIT 50",
                tenantId);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @GetMapping("/messages/channels/{channelId}/messages")
    public ResponseEntity<Map<String, Object>> listMessages(
            @PathVariable UUID channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int limit = Math.min(size, 100);
        int offset = page * limit;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM clinical_messages WHERE channel_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                channelId, limit, offset);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    @PostMapping("/messages")
    @Transactional
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        UUID channelId = UUID.fromString(body.get("channelId").toString());
        jdbc.update("""
            INSERT INTO clinical_messages (id, tenant_id, channel_id, sender_id, sender_name, content, message_type)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
                id, tenantId, channelId,
                body.getOrDefault("senderId", ""),
                body.getOrDefault("senderName", ""),
                body.getOrDefault("content", ""),
                body.getOrDefault("messageType", "TEXT"));

        jdbc.update("UPDATE message_channels SET last_message_at = NOW() WHERE id = ?", channelId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", Map.of("id", id.toString())));
    }
}
