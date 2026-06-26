package zw.gov.mohcc.impilo.learning.fundo;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.SessionAttendanceEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.SessionCheckinTokenEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.ScheduledLearningSessionRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.SessionAttendanceRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.SessionCheckinTokenRepository;

/**
 * Session attendance + check-in (A2). Facilitators bulk-mark attendance; learners
 * self check-in with a code/QR token. Attendance reuses the subject_type/subject_id
 * idiom and optionally links to a course enrolment.
 */
@Service
public class FundoAttendanceService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long DEFAULT_TOKEN_TTL_MINUTES = 240L;

    private final SessionAttendanceRepository attendanceRepository;
    private final SessionCheckinTokenRepository tokenRepository;
    private final ScheduledLearningSessionRepository sessionRepository;

    public FundoAttendanceService(
            SessionAttendanceRepository attendanceRepository,
            SessionCheckinTokenRepository tokenRepository,
            ScheduledLearningSessionRepository sessionRepository) {
        this.attendanceRepository = attendanceRepository;
        this.tokenRepository = tokenRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public Map<String, Object> createCheckinToken(UUID tenantId, String sessionId, String actorId, Map<String, Object> body) {
        UUID session = UUID.fromString(sessionId);
        sessionRepository.findByTenantIdAndId(tenantId, session)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        SessionCheckinTokenEntity t = new SessionCheckinTokenEntity();
        t.setTenantId(tenantId);
        t.setSessionId(session);
        t.setToken(generateCode());
        long ttl = body.get("ttlMinutes") == null ? DEFAULT_TOKEN_TTL_MINUTES : Long.parseLong(body.get("ttlMinutes").toString());
        t.setExpiresAt(OffsetDateTime.now().plusMinutes(ttl));
        t.setSingleUse(Boolean.parseBoolean(String.valueOf(body.getOrDefault("singleUse", false))));
        t.setCreatedBy(actorId);
        tokenRepository.save(t);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId().toString());
        m.put("sessionId", sessionId);
        m.put("token", t.getToken());
        m.put("expiresAt", t.getExpiresAt().toString());
        m.put("singleUse", t.isSingleUse());
        return m;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> markAttendance(UUID tenantId, String sessionId, String actorId, Map<String, Object> body) {
        UUID session = UUID.fromString(sessionId);
        sessionRepository.findByTenantIdAndId(tenantId, session)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        List<Map<String, Object>> entries = (List<Map<String, Object>>) body.getOrDefault("entries", List.of());
        int marked = 0;
        for (Map<String, Object> e : entries) {
            String subjectType = String.valueOf(e.get("subjectType"));
            String subjectId = String.valueOf(e.get("subjectId"));
            if (subjectType == null || subjectId == null || subjectType.isBlank() || subjectId.isBlank()) {
                continue;
            }
            String status = String.valueOf(e.getOrDefault("status", "PRESENT"));
            upsert(tenantId, session, subjectType, subjectId, status, "MANUAL", actorId,
                    e.get("enrolmentId") == null ? null : UUID.fromString(e.get("enrolmentId").toString()));
            marked++;
        }
        return Map.of("sessionId", sessionId, "marked", marked);
    }

    @Transactional
    public Map<String, Object> selfCheckin(UUID tenantId, String sessionId, Map<String, Object> body) {
        UUID session = UUID.fromString(sessionId);
        String tokenStr = String.valueOf(body.get("token"));
        SessionCheckinTokenEntity token = tokenRepository.findByTenantIdAndToken(tenantId, tokenStr)
                .orElseThrow(() -> new IllegalArgumentException("invalid check-in token"));
        if (!token.getSessionId().equals(session)) {
            throw new IllegalArgumentException("token does not match session");
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("check-in token expired");
        }
        if (token.isSingleUse() && token.getConsumedAt() != null) {
            throw new IllegalArgumentException("check-in token already used");
        }
        String subjectType = String.valueOf(body.get("subjectType"));
        String subjectId = String.valueOf(body.get("subjectId"));
        String method = String.valueOf(body.getOrDefault("method", "CODE"));
        SessionAttendanceEntity a = upsert(tenantId, session, subjectType, subjectId, "PRESENT", method, subjectId,
                body.get("enrolmentId") == null ? null : UUID.fromString(body.get("enrolmentId").toString()));
        if (token.isSingleUse()) {
            token.setConsumedAt(OffsetDateTime.now());
            tokenRepository.save(token);
        }
        return Map.of("sessionId", sessionId, "attendanceId", a.getId().toString(), "status", a.getStatus());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listAttendance(UUID tenantId, String sessionId) {
        List<Map<String, Object>> items = attendanceRepository
                .findByTenantIdAndSessionId(tenantId, UUID.fromString(sessionId)).stream()
                .map(FundoAttendanceService::view).toList();
        return Map.of("sessionId", sessionId, "items", items);
    }

    private SessionAttendanceEntity upsert(UUID tenantId, UUID session, String subjectType, String subjectId,
            String status, String method, String recordedBy, UUID enrolmentId) {
        SessionAttendanceEntity a = attendanceRepository
                .findBySessionIdAndSubjectTypeAndSubjectId(session, subjectType, subjectId)
                .orElseGet(SessionAttendanceEntity::new);
        a.setTenantId(tenantId);
        a.setSessionId(session);
        a.setSubjectType(subjectType);
        a.setSubjectId(subjectId);
        a.setStatus(status);
        a.setCheckInMethod(method);
        if (enrolmentId != null) {
            a.setEnrolmentId(enrolmentId);
        }
        if ("PRESENT".equals(status) || "LATE".equals(status)) {
            a.setCheckInAt(OffsetDateTime.now());
        }
        a.setRecordedBy(recordedBy);
        a.setUpdatedAt(OffsetDateTime.now());
        return attendanceRepository.save(a);
    }

    private static Map<String, Object> view(SessionAttendanceEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("subjectType", a.getSubjectType());
        m.put("subjectId", a.getSubjectId());
        m.put("status", a.getStatus());
        m.put("checkInMethod", a.getCheckInMethod());
        m.put("checkInAt", a.getCheckInAt() == null ? null : a.getCheckInAt().toString());
        m.put("enrolmentId", a.getEnrolmentId() == null ? null : a.getEnrolmentId().toString());
        return m;
    }

    private static String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
