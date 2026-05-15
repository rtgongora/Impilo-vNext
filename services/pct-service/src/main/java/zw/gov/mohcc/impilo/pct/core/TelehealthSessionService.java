package zw.gov.mohcc.impilo.pct.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.TelehealthSessionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.TelehealthSessionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class TelehealthSessionService {
    private static final Set<String> ACTIVE_STATUSES = Set.of("SCHEDULED", "IN_PROGRESS");

    private final TelehealthSessionRepository telehealthRepository;
    private final EncounterRepository encounterRepository;

    public TelehealthSessionService(TelehealthSessionRepository telehealthRepository,
                                    EncounterRepository encounterRepository) {
        this.telehealthRepository = telehealthRepository;
        this.encounterRepository = encounterRepository;
    }

    @Transactional
    public TelehealthSessionEntity createSession(java.util.Map<String, Object> request) {
        TrustContext ctx = TrustContextHolder.require();
        String patientId = str(request, "patientId", "patient_id");
        if (patientId == null || patientId.isBlank()) {
            throw new PctDomainException("MISSING_PATIENT_ID", 400, "patient_id is required");
        }

        TelehealthSessionEntity entity = new TelehealthSessionEntity();
        entity.setSessionId(UUID.randomUUID());
        entity.setTenantId(ctx.tenantId());
        entity.setPatientCpid(patientId);
        entity.setProviderId(str(request, "providerId", "provider_id"));
        entity.setFacilityId(parseUuid(str(request, "facilityId", "facility_id"), ctx.facilityId()));
        entity.setWorkspaceId(ctx.workspaceId());
        entity.setSessionType(defaulted(str(request, "sessionType", "session_type"), "VIDEO").toUpperCase(Locale.ROOT));
        entity.setVirtualMode(mapVirtualMode(entity.getSessionType()));
        entity.setStatus("SCHEDULED");
        entity.setChannel("clinical");
        entity.setNotes(str(request, "notes"));
        entity.setScheduledAt(parseDate(str(request, "scheduledAt", "scheduled_at"), OffsetDateTime.now().plusHours(1)));

        String encounterId = str(request, "encounterId", "encounter_id");
        if (encounterId != null && !encounterId.isBlank()) {
            try {
                Long numeric = Long.parseLong(encounterId);
                EncounterEntity encounter = encounterRepository.findByTenantIdAndId(ctx.tenantId(), numeric)
                        .orElseThrow(() -> new PctDomainException("MISSING_ENCOUNTER", 404, "Encounter not found: " + encounterId));
                entity.setEncounterId(numeric);
                entity.setJourneyId(encounter.getJourneyId());
                entity.setPatientCpid(encounter.getSubjectCpid());
            } catch (NumberFormatException ex) {
                throw new PctDomainException("INVALID_ENCOUNTER_ID", 400, "encounter_id must be numeric");
            }
        }

        String referralId = str(request, "referralId", "referral_id");
        if (referralId != null && !referralId.isBlank()) {
            entity.setReferralId(parseUuid(referralId, null));
        }
        return telehealthRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public TelehealthSessionEntity getSession(UUID sessionId) {
        TrustContext ctx = TrustContextHolder.require();
        return telehealthRepository.findByTenantIdAndSessionId(ctx.tenantId(), sessionId)
                .orElseThrow(() -> new PctDomainException("SESSION_NOT_FOUND", 404, "Telehealth session not found: " + sessionId));
    }

    @Transactional(readOnly = true)
    public List<TelehealthSessionEntity> listByPatient(String patientCpid, String status) {
        TrustContext ctx = TrustContextHolder.require();
        List<TelehealthSessionEntity> items = telehealthRepository.findByTenantIdAndPatientCpidOrderByCreatedAtDesc(ctx.tenantId(), patientCpid);
        return filterStatus(items, status);
    }

    @Transactional(readOnly = true)
    public List<TelehealthSessionEntity> listByFacility(UUID facilityId, String status) {
        TrustContext ctx = TrustContextHolder.require();
        List<TelehealthSessionEntity> items = telehealthRepository.findByTenantIdAndFacilityIdOrderByCreatedAtDesc(ctx.tenantId(), facilityId);
        return filterStatus(items, status);
    }

    @Transactional
    public TelehealthSessionEntity join(UUID sessionId) {
        TelehealthSessionEntity session = getSession(sessionId);
        if (!ACTIVE_STATUSES.contains(session.getStatus())) {
            throw new PctDomainException("INVALID_TRANSITION", 409,
                    "Cannot join telehealth session in status " + session.getStatus());
        }
        session.setStatus("IN_PROGRESS");
        if (session.getStartedAt() == null) {
            session.setStartedAt(OffsetDateTime.now());
        }
        return telehealthRepository.save(session);
    }

    @Transactional
    public TelehealthSessionEntity end(UUID sessionId, String notes) {
        TelehealthSessionEntity session = getSession(sessionId);
        if (!ACTIVE_STATUSES.contains(session.getStatus())) {
            throw new PctDomainException("INVALID_TRANSITION", 409,
                    "Cannot end telehealth session in status " + session.getStatus());
        }
        session.setStatus("COMPLETED");
        session.setEndedAt(OffsetDateTime.now());
        if (notes != null && !notes.isBlank()) {
            session.setNotes(notes);
        }
        if (session.getStartedAt() != null && session.getEndedAt() != null) {
            long seconds = java.time.Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds();
            session.setDurationSeconds((int) Math.max(0, seconds));
        }
        return telehealthRepository.save(session);
    }

    private List<TelehealthSessionEntity> filterStatus(List<TelehealthSessionEntity> sessions, String status) {
        if (status == null || status.isBlank()) {
            return sessions;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return sessions.stream()
                .filter(item -> normalized.equals(item.getStatus()))
                .toList();
    }

    private String mapVirtualMode(String sessionType) {
        return switch (sessionType.toUpperCase(Locale.ROOT)) {
            case "AUDIO" -> "audio";
            case "CHAT" -> "chat";
            case "ASYNC_REVIEW" -> "async";
            case "CASE_REVIEW" -> "board";
            default -> "video";
        };
    }

    private String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String str(java.util.Map<String, Object> src, String... keys) {
        for (String key : keys) {
            Object value = src.get(key);
            if (value != null) return value.toString();
        }
        return null;
    }

    private UUID parseUuid(String raw, UUID fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new PctDomainException("INVALID_UUID", 400, "invalid UUID: " + raw);
        }
    }

    private OffsetDateTime parseDate(String raw, OffsetDateTime fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return OffsetDateTime.parse(raw);
        } catch (Exception ex) {
            throw new PctDomainException("INVALID_DATETIME", 400, "invalid ISO-8601 timestamp: " + raw);
        }
    }
}
