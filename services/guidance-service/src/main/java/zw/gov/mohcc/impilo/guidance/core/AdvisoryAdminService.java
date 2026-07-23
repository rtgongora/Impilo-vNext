package zw.gov.mohcc.impilo.guidance.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.guidance.persistence.entity.ServiceAdvisoryEntity;
import zw.gov.mohcc.impilo.guidance.persistence.repository.ServiceAdvisoryRepository;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service Advisory authoring + lifecycle engine.
 *
 * <p>Lifecycle: DRAFT → SCHEDULED → PUBLISHED → WITHDRAWN | EXPIRED. Governance: an advisory
 * whose severity is EMERGENCY_ALERT or IMPORTANT_DISRUPTION must have an approver
 * ({@code approvedBy}) set before it may be PUBLISHED — the approval gate is enforced here,
 * not in the controller.</p>
 */
@Service
public class AdvisoryAdminService {

    private static final Logger log = LoggerFactory.getLogger(AdvisoryAdminService.class);

    private static final Set<String> SEVERITIES =
            Set.of("INFORMATIONAL", "SERVICE_NOTICE", "IMPORTANT_DISRUPTION", "EMERGENCY_ALERT");
    private static final Set<String> VARIANTS =
            Set.of("MODAL", "BANNER", "BUBBLE", "INLINE", "EMERGENCY");
    /** Severities that require an approver before they may be PUBLISHED. */
    private static final Set<String> APPROVAL_REQUIRED =
            Set.of("EMERGENCY_ALERT", "IMPORTANT_DISRUPTION");

    /**
     * Notice categories that require an approver before PUBLISHED regardless of severity —
     * the legal guardrail for enforcement/recall notices (V017). A regulatory or safety-recall
     * notice reaches the public archive only when a named authority has approved it; there is
     * no path from internal disciplinary/case data to the public lane.
     */
    private static final Set<String> APPROVAL_REQUIRED_CATEGORIES =
            Set.of("REGULATORY_NOTICE", "SAFETY_RECALL");

    /** Valid notice categories (V017). */
    private static final Set<String> NOTICE_CATEGORIES = Set.of(
            "SERVICE_STATUS", "PUBLIC_HEALTH_CAMPAIGN", "SAFETY_RECALL",
            "REGULATORY_NOTICE", "DISASTER_ALERT", "PROCUREMENT_NOTICE");

    private final ServiceAdvisoryRepository repo;
    private final ObjectMapper objectMapper;

    public AdvisoryAdminService(ServiceAdvisoryRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    /** Raised when a request is invalid or a lifecycle transition is not permitted. */
    public static class AdvisoryValidationException extends RuntimeException {
        private final String code;
        public AdvisoryValidationException(String code, String message) {
            super(message);
            this.code = code;
        }
        public String getCode() { return code; }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String tenantId, String status) {
        List<ServiceAdvisoryEntity> rows = (status == null || status.isBlank())
                ? repo.findByTenantIdOrderByUpdatedAtDesc(tenantId)
                : repo.findByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId, status.trim().toUpperCase(Locale.ROOT));
        return rows.stream().map(this::toAdminView).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String tenantId, UUID id) {
        return toAdminView(require(tenantId, id));
    }

    @Transactional
    public Map<String, Object> create(String tenantId, String actor, Map<String, Object> body) {
        ServiceAdvisoryEntity a = new ServiceAdvisoryEntity();
        a.setTenantId(tenantId == null || tenantId.isBlank() ? "national-spine" : tenantId);
        a.setStatus("DRAFT");
        a.setCreatedBy(actor);
        applyEditable(a, body, true);
        ServiceAdvisoryEntity saved = repo.save(a);
        log.debug("Advisory created: id={} severity={} by={}", saved.getId(), saved.getSeverity(), actor);
        return toAdminView(saved);
    }

    @Transactional
    public Map<String, Object> update(String tenantId, UUID id, Map<String, Object> body) {
        ServiceAdvisoryEntity a = require(tenantId, id);
        if ("PUBLISHED".equals(a.getStatus())) {
            throw new AdvisoryValidationException("ADVISORY_LOCKED",
                    "A PUBLISHED advisory cannot be edited; withdraw it first.");
        }
        applyEditable(a, body, false);
        return toAdminView(repo.save(a));
    }

    /** Set the approver — the governance step required before EMERGENCY/IMPORTANT publish. */
    @Transactional
    public Map<String, Object> approve(String tenantId, UUID id, String approver) {
        ServiceAdvisoryEntity a = require(tenantId, id);
        if (approver == null || approver.isBlank()) {
            throw new AdvisoryValidationException("APPROVER_REQUIRED", "An approver identity is required.");
        }
        a.setApprovedBy(approver);
        log.debug("Advisory approved: id={} by={}", id, approver);
        return toAdminView(repo.save(a));
    }

    @Transactional
    public Map<String, Object> schedule(String tenantId, UUID id) {
        ServiceAdvisoryEntity a = require(tenantId, id);
        requireStatus(a, "DRAFT");
        if (a.getStartsAt() == null) {
            throw new AdvisoryValidationException("STARTS_AT_REQUIRED",
                    "A scheduled advisory must have a startsAt time.");
        }
        return transitionTo(a, "SCHEDULED");
    }

    @Transactional
    public Map<String, Object> publish(String tenantId, UUID id) {
        ServiceAdvisoryEntity a = require(tenantId, id);
        if (!Set.of("DRAFT", "SCHEDULED").contains(a.getStatus())) {
            throw new AdvisoryValidationException("ILLEGAL_TRANSITION",
                    "Only a DRAFT or SCHEDULED advisory can be published.");
        }
        boolean needsApproval = APPROVAL_REQUIRED.contains(a.getSeverity())
                || APPROVAL_REQUIRED_CATEGORIES.contains(a.getCategory());
        if (needsApproval && (a.getApprovedBy() == null || a.getApprovedBy().isBlank())) {
            String reason = APPROVAL_REQUIRED_CATEGORIES.contains(a.getCategory())
                    ? a.getCategory() + " notices"
                    : a.getSeverity() + " advisories";
            throw new AdvisoryValidationException("APPROVAL_REQUIRED",
                    reason + " must be approved before they can be published.");
        }
        return transitionTo(a, "PUBLISHED");
    }

    @Transactional
    public Map<String, Object> withdraw(String tenantId, UUID id) {
        ServiceAdvisoryEntity a = require(tenantId, id);
        requireStatus(a, "PUBLISHED");
        return transitionTo(a, "WITHDRAWN");
    }

    @Transactional
    public Map<String, Object> expire(String tenantId, UUID id) {
        ServiceAdvisoryEntity a = require(tenantId, id);
        if (!Set.of("PUBLISHED", "SCHEDULED").contains(a.getStatus())) {
            throw new AdvisoryValidationException("ILLEGAL_TRANSITION",
                    "Only a PUBLISHED or SCHEDULED advisory can be expired.");
        }
        return transitionTo(a, "EXPIRED");
    }

    @Transactional
    public void delete(String tenantId, UUID id) {
        ServiceAdvisoryEntity a = require(tenantId, id);
        repo.delete(a);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> transitionTo(ServiceAdvisoryEntity a, String status) {
        a.setStatus(status);
        log.debug("Advisory transition: id={} → {}", a.getId(), status);
        return toAdminView(repo.save(a));
    }

    private ServiceAdvisoryEntity require(String tenantId, UUID id) {
        return repo.findByIdAndTenantId(id, tenantId == null || tenantId.isBlank() ? "national-spine" : tenantId)
                .orElseThrow(() -> new AdvisoryValidationException("ADVISORY_NOT_FOUND",
                        "No advisory exists with that id."));
    }

    private void requireStatus(ServiceAdvisoryEntity a, String expected) {
        if (!expected.equals(a.getStatus())) {
            throw new AdvisoryValidationException("ILLEGAL_TRANSITION",
                    "Expected status " + expected + " but advisory is " + a.getStatus() + ".");
        }
    }

    @SuppressWarnings("unchecked")
    private void applyEditable(ServiceAdvisoryEntity a, Map<String, Object> body, boolean creating) {
        Map<String, Object> b = body == null ? Map.of() : body;

        if (creating || b.containsKey("title")) {
            String title = str(b, "title");
            if (creating && (title == null || title.isBlank())) {
                throw new AdvisoryValidationException("TITLE_REQUIRED", "title is required.");
            }
            if (title != null) a.setTitle(title);
        }
        if (creating || b.containsKey("body")) {
            String text = str(b, "body");
            if (creating && (text == null || text.isBlank())) {
                throw new AdvisoryValidationException("BODY_REQUIRED", "body is required.");
            }
            if (text != null) a.setBody(text);
        }
        if (b.containsKey("category")) {
            String category = upper(str(b, "category"));
            if (category != null) {
                if (!NOTICE_CATEGORIES.contains(category)) {
                    throw new AdvisoryValidationException("INVALID_CATEGORY", "Unknown category: " + category);
                }
                a.setCategory(category);
            }
        }
        if (creating || b.containsKey("severity")) {
            String severity = upper(str(b, "severity"));
            if (severity != null) {
                if (!SEVERITIES.contains(severity)) {
                    throw new AdvisoryValidationException("INVALID_SEVERITY", "Unknown severity: " + severity);
                }
                a.setSeverity(severity);
            } else if (creating) {
                throw new AdvisoryValidationException("SEVERITY_REQUIRED", "severity is required.");
            }
        }
        if (creating || b.containsKey("variant")) {
            String variant = upper(str(b, "variant"));
            if (variant != null) {
                if (!VARIANTS.contains(variant)) {
                    throw new AdvisoryValidationException("INVALID_VARIANT", "Unknown variant: " + variant);
                }
                a.setVariant(variant);
            } else if (creating) {
                throw new AdvisoryValidationException("VARIANT_REQUIRED", "variant is required.");
            }
        }
        if (b.containsKey("targeting")) {
            a.setTargeting(toJson(b.get("targeting")));
        }
        if (b.containsKey("primaryActions")) {
            a.setPrimaryActions(toJson(b.get("primaryActions")));
        }
        if (b.containsKey("startsAt")) {
            a.setStartsAt(parseTime(str(b, "startsAt")));
        }
        if (b.containsKey("expiresAt")) {
            a.setExpiresAt(parseTime(str(b, "expiresAt")));
        }
        if (b.containsKey("approvedBy")) {
            a.setApprovedBy(str(b, "approvedBy"));
        }
    }

    private String toJson(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s.isBlank() ? null : s;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new AdvisoryValidationException("INVALID_JSON", "Could not serialize JSON field: " + e.getMessage());
        }
    }

    private OffsetDateTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value.trim());
        } catch (Exception e) {
            throw new AdvisoryValidationException("INVALID_TIMESTAMP",
                    "Timestamps must be ISO-8601 with offset, e.g. 2026-07-20T09:00:00Z.");
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private static String upper(String v) {
        return v == null ? null : v.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> toAdminView(ServiceAdvisoryEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("tenantId", a.getTenantId());
        m.put("category", a.getCategory());
        m.put("title", a.getTitle());
        m.put("body", a.getBody());
        m.put("severity", a.getSeverity());
        m.put("variant", a.getVariant());
        m.put("status", a.getStatus());
        m.put("targeting", rawJson(a.getTargeting()));
        m.put("primaryActions", rawJson(a.getPrimaryActions()));
        m.put("startsAt", a.getStartsAt() == null ? null : a.getStartsAt().toString());
        m.put("expiresAt", a.getExpiresAt() == null ? null : a.getExpiresAt().toString());
        m.put("createdBy", a.getCreatedBy());
        m.put("approvedBy", a.getApprovedBy());
        m.put("createdAt", a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
        m.put("updatedAt", a.getUpdatedAt() == null ? null : a.getUpdatedAt().toString());
        return m;
    }

    private Object rawJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return json;
        }
    }
}
