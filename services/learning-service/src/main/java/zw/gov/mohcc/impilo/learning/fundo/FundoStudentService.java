package zw.gov.mohcc.impilo.learning.fundo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.StudentApplicationEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.StudentProfileEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.StudentApplicationRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.StudentProfileRepository;

/**
 * Pre-service admission lifecycle (A5): applications → decision → admission
 * (student profile). Student number is generated on admission.
 */
@Service
public class FundoStudentService {

    private final StudentApplicationRepository applicationRepository;
    private final StudentProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    public FundoStudentService(
            StudentApplicationRepository applicationRepository,
            StudentProfileRepository profileRepository,
            ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.profileRepository = profileRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> submitApplication(UUID tenantId, Map<String, Object> body) {
        StudentApplicationEntity a = new StudentApplicationEntity();
        a.setTenantId(tenantId);
        a.setProgramId(UUID.fromString(reqStr(body, "programId")));
        a.setTermId(uuidOrNull(body.get("termId")));
        a.setApplicantSubjectType(nullable(body, "applicantSubjectType"));
        a.setApplicantSubjectId(nullable(body, "applicantSubjectId"));
        a.setApplicantName(reqStr(body, "applicantName"));
        a.setApplicantContactJson(writeJson(body.get("contact")));
        a.setStatus("SUBMITTED");
        applicationRepository.save(a);
        return applicationView(a);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listApplications(UUID tenantId, String status, String programId, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<StudentApplicationEntity> rows;
        if (status != null && !status.isBlank()) {
            rows = applicationRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status, page);
        } else if (programId != null && !programId.isBlank()) {
            rows = applicationRepository.findByTenantIdAndProgramIdOrderByCreatedAtDesc(tenantId, UUID.fromString(programId), page);
        } else {
            rows = applicationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, page);
        }
        return Map.of("items", rows.stream().map(FundoStudentService::applicationView).toList(), "limit", limit);
    }

    @Transactional
    public Map<String, Object> decideApplication(UUID tenantId, String applicationId, String actorId, Map<String, Object> body) {
        StudentApplicationEntity a = applicationRepository.findByTenantIdAndId(tenantId, UUID.fromString(applicationId))
                .orElseThrow(() -> new IllegalArgumentException("application not found"));
        String status = reqStr(body, "status");
        a.setStatus(status);
        a.setDecisionBy(actorId);
        a.setDecisionAt(OffsetDateTime.now());
        a.setDecisionNotes(nullable(body, "decisionNotes"));
        a.setUpdatedAt(OffsetDateTime.now());
        applicationRepository.save(a);
        return applicationView(a);
    }

    /** Admit a student — from an accepted application or directly. Generates a student number. */
    @Transactional
    public Map<String, Object> admitStudent(UUID tenantId, String actorId, Map<String, Object> body) {
        StudentProfileEntity p = new StudentProfileEntity();
        p.setTenantId(tenantId);
        UUID applicationId = uuidOrNull(body.get("applicationId"));
        StudentApplicationEntity app = null;
        if (applicationId != null) {
            app = applicationRepository.findByTenantIdAndId(tenantId, applicationId)
                    .orElseThrow(() -> new IllegalArgumentException("application not found"));
            p.setProgramId(app.getProgramId());
            p.setSubjectType(app.getApplicantSubjectType());
            p.setSubjectId(app.getApplicantSubjectId());
            p.setCurrentTermId(app.getTermId());
            p.setAdmissionApplicationId(app.getId());
        } else {
            p.setProgramId(UUID.fromString(reqStr(body, "programId")));
            p.setSubjectType(nullable(body, "subjectType"));
            p.setSubjectId(nullable(body, "subjectId"));
            p.setCurrentTermId(uuidOrNull(body.get("termId")));
        }
        p.setStudentNumber(generateStudentNumber());
        p.setAdmittedAt(OffsetDateTime.now());
        p.setStatus("ADMITTED");
        p.setCreatedBy(actorId);
        profileRepository.save(p);

        if (app != null && !"ACCEPTED".equals(app.getStatus())) {
            app.setStatus("ACCEPTED");
            app.setDecisionBy(actorId);
            app.setDecisionAt(OffsetDateTime.now());
            app.setUpdatedAt(OffsetDateTime.now());
            applicationRepository.save(app);
        }
        return profileView(p);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listStudents(UUID tenantId, String programId, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<StudentProfileEntity> rows = (programId == null || programId.isBlank())
                ? profileRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, page)
                : profileRepository.findByTenantIdAndProgramIdOrderByCreatedAtDesc(tenantId, UUID.fromString(programId), page);
        return Map.of("items", rows.stream().map(FundoStudentService::profileView).toList(), "limit", limit);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStudent(UUID tenantId, String studentId) {
        return profileRepository.findByTenantIdAndId(tenantId, UUID.fromString(studentId))
                .map(FundoStudentService::profileView)
                .orElseThrow(() -> new IllegalArgumentException("student not found"));
    }

    private static Map<String, Object> applicationView(StudentApplicationEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("programId", a.getProgramId().toString());
        m.put("termId", a.getTermId() == null ? null : a.getTermId().toString());
        m.put("applicantName", a.getApplicantName());
        m.put("applicantSubjectId", a.getApplicantSubjectId());
        m.put("status", a.getStatus());
        m.put("decisionNotes", a.getDecisionNotes());
        m.put("createdAt", a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
        return m;
    }

    private static Map<String, Object> profileView(StudentProfileEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId().toString());
        m.put("studentNumber", p.getStudentNumber());
        m.put("programId", p.getProgramId().toString());
        m.put("subjectType", p.getSubjectType());
        m.put("subjectId", p.getSubjectId());
        m.put("currentTermId", p.getCurrentTermId() == null ? null : p.getCurrentTermId().toString());
        m.put("status", p.getStatus());
        m.put("admittedAt", p.getAdmittedAt() == null ? null : p.getAdmittedAt().toString());
        return m;
    }

    private static String generateStudentNumber() {
        return "STU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String writeJson(Object o) {
        if (o == null) return null;
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private static UUID uuidOrNull(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        return UUID.fromString(o.toString());
    }

    private static String reqStr(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return v.toString();
    }

    private static String nullable(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }
}
