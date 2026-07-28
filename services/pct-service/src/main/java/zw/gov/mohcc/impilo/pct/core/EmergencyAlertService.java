package zw.gov.mohcc.impilo.pct.core;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyAlertEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyAlertRepository;

/**
 * The responder-facing side of {@code emergency_alert} (pct V203, W5) — acknowledge, respond, close.
 * The sweep job ({@link EmergencyAlertSweepJob}) only raises and ages alerts to OVERDUE; this is where
 * a human acts on one. OVERDUE is still an open status ({@code EmergencyAlertRepository.OPEN_STATUSES})
 * so every action here is reachable from it too — an alert that aged past its window is not thereby
 * un-actionable.
 */
@Service
public class EmergencyAlertService {

    private static final Set<String> ACKNOWLEDGEABLE_FROM = Set.of("RAISED", "OVERDUE");
    private static final Set<String> RESPONDABLE_FROM = Set.of("RAISED", "ACKNOWLEDGED", "OVERDUE");
    private static final Set<String> CLOSABLE_FROM = Set.of("RAISED", "ACKNOWLEDGED", "RESPONDED", "OVERDUE");

    private final EmergencyAlertRepository alertRepository;

    public EmergencyAlertService(EmergencyAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Transactional
    public EmergencyAlertEntity acknowledge(UUID alertId, UUID tenantId, Map<String, Object> body) {
        EmergencyAlertEntity alert = load(alertId, tenantId);
        if (!ACKNOWLEDGEABLE_FROM.contains(alert.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Alert " + alertId + " is " + alert.getStatus() + " — it cannot be acknowledged");
        }
        String acknowledgedBy = str(body, "acknowledgedBy", "acknowledged_by");
        if (acknowledgedBy == null || acknowledgedBy.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "acknowledged_by is required");
        }
        alert.setStatus("ACKNOWLEDGED");
        alert.setAcknowledgedBy(acknowledgedBy);
        alert.setAcknowledgedAt(OffsetDateTime.now());
        return alertRepository.save(alert);
    }

    @Transactional
    public EmergencyAlertEntity respond(UUID alertId, UUID tenantId, Map<String, Object> body) {
        EmergencyAlertEntity alert = load(alertId, tenantId);
        if (!RESPONDABLE_FROM.contains(alert.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Alert " + alertId + " is " + alert.getStatus() + " — it cannot be responded to");
        }
        String respondedBy = str(body, "respondedBy", "responded_by");
        if (respondedBy == null || respondedBy.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "responded_by is required");
        }
        OffsetDateTime now = OffsetDateTime.now();
        // Responding without first acknowledging is legitimate (a responder can act directly), but
        // chk_ea_ack_recorded requires acknowledged_at whenever status is ACKNOWLEDGED or RESPONDED —
        // so a direct response also stamps the acknowledgement, by the same actor, at the same instant.
        if (alert.getAcknowledgedAt() == null) {
            alert.setAcknowledgedBy(respondedBy);
            alert.setAcknowledgedAt(now);
        }
        alert.setStatus("RESPONDED");
        alert.setRespondedBy(respondedBy);
        alert.setRespondedAt(now);
        alert.setResponseNote(str(body, "responseNote", "response_note"));
        return alertRepository.save(alert);
    }

    @Transactional
    public EmergencyAlertEntity close(UUID alertId, UUID tenantId, Map<String, Object> body) {
        EmergencyAlertEntity alert = load(alertId, tenantId);
        if (!CLOSABLE_FROM.contains(alert.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Alert " + alertId + " is already " + alert.getStatus());
        }
        String closeReason = str(body, "closeReason", "close_reason");
        if (closeReason == null || closeReason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "close_reason is required");
        }
        alert.setStatus("CLOSED");
        alert.setClosedAt(OffsetDateTime.now());
        alert.setCloseReason(closeReason);
        return alertRepository.save(alert);
    }

    @Transactional(readOnly = true)
    public List<EmergencyAlertEntity> forEpisode(UUID episodeId, UUID tenantId) {
        return alertRepository.findByTenantIdAndEpisodeIdOrderByRaisedAtDesc(tenantId, episodeId);
    }

    /** The responder board: open alerts for a facility (or the whole tenant when facilityId is null). */
    @Transactional(readOnly = true)
    public List<EmergencyAlertEntity> board(UUID tenantId, UUID facilityId) {
        return alertRepository.findOpenForFacility(tenantId, facilityId);
    }

    private EmergencyAlertEntity load(UUID alertId, UUID tenantId) {
        return alertRepository.findByAlertIdAndTenantId(alertId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Emergency alert not found"));
    }

    private static String str(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return null;
    }
}
