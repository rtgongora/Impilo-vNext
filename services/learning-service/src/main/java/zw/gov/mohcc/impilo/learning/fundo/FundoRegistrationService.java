package zw.gov.mohcc.impilo.learning.fundo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.ClinicalPlacementEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseRegistrationEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.GraduationRecordEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.StudentProfileEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.AcademicProgramRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.ClinicalPlacementRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRegistrationRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.GraduationRecordRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.StudentProfileRepository;

/**
 * Term course registration (reuses enrolment), clinical placement + preceptor
 * sign-off, and graduation (reuses certificate). Closes the pre-service lifecycle (A6).
 */
@Service
public class FundoRegistrationService {

    private final StudentProfileRepository profileRepository;
    private final AcademicProgramRepository programRepository;
    private final CourseRegistrationRepository registrationRepository;
    private final ClinicalPlacementRepository placementRepository;
    private final GraduationRecordRepository graduationRepository;
    private final FundoEnrolmentService enrolmentService;

    public FundoRegistrationService(
            StudentProfileRepository profileRepository,
            AcademicProgramRepository programRepository,
            CourseRegistrationRepository registrationRepository,
            ClinicalPlacementRepository placementRepository,
            GraduationRecordRepository graduationRepository,
            FundoEnrolmentService enrolmentService) {
        this.profileRepository = profileRepository;
        this.programRepository = programRepository;
        this.registrationRepository = registrationRepository;
        this.placementRepository = placementRepository;
        this.graduationRepository = graduationRepository;
        this.enrolmentService = enrolmentService;
    }

    @Transactional
    public Map<String, Object> registerCourse(UUID tenantId, String studentId, String actorId, Map<String, Object> body) {
        StudentProfileEntity student = profile(tenantId, studentId);
        UUID courseId = UUID.fromString(reqStr(body, "courseId"));
        UUID termId = uuidOrNull(body.get("termId"));
        if (termId == null) {
            termId = student.getCurrentTermId();
        }

        // Reuse enrolment for delivery — academic registration is a thin wrapper.
        Map<String, Object> enrolment = enrolmentService.create(new FundoEnrolmentService.EnrolmentRequest(
                tenantId, subjectType(student), subjectId(student), courseId, null, "ACADEMIC", actorId, null));
        UUID enrolmentId = enrolment.get("id") == null ? null : UUID.fromString(enrolment.get("id").toString());

        CourseRegistrationEntity r = new CourseRegistrationEntity();
        r.setTenantId(tenantId);
        r.setStudentProfileId(student.getId());
        r.setTermId(termId);
        r.setCourseId(courseId);
        r.setEnrolmentId(enrolmentId);
        r.setStatus("REGISTERED");
        r.setCreatedBy(actorId);
        registrationRepository.save(r);
        return registrationView(r);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listRegistrations(UUID tenantId, String studentId) {
        StudentProfileEntity student = profile(tenantId, studentId);
        List<Map<String, Object>> items = registrationRepository
                .findByTenantIdAndStudentProfileIdOrderByCreatedAtDesc(tenantId, student.getId())
                .stream().map(FundoRegistrationService::registrationView).toList();
        return Map.of("studentId", studentId, "items", items);
    }

    @Transactional
    public Map<String, Object> createPlacement(UUID tenantId, String studentId, String actorId, Map<String, Object> body) {
        StudentProfileEntity student = profile(tenantId, studentId);
        ClinicalPlacementEntity p = new ClinicalPlacementEntity();
        p.setTenantId(tenantId);
        p.setStudentProfileId(student.getId());
        p.setTermId(uuidOrNull(body.get("termId")));
        p.setVenueId(uuidOrNull(body.get("venueId")));
        p.setPreceptorFacilitatorId(uuidOrNull(body.get("preceptorFacilitatorId")));
        p.setTitle(nullable(body, "title"));
        p.setStartsOn(dateOrNull(body.get("startsOn")));
        p.setEndsOn(dateOrNull(body.get("endsOn")));
        p.setSignoffStatus("PENDING");
        p.setCreatedBy(actorId);
        placementRepository.save(p);
        return placementView(p);
    }

    @Transactional
    public Map<String, Object> signoffPlacement(UUID tenantId, String placementId, String actorId, Map<String, Object> body) {
        ClinicalPlacementEntity p = placementRepository.findByTenantIdAndId(tenantId, UUID.fromString(placementId))
                .orElseThrow(() -> new IllegalArgumentException("placement not found"));
        p.setSignoffStatus(str(body, "signoffStatus", "SIGNED_OFF"));
        p.setSignedOffBy(actorId);
        p.setSignedOffAt(OffsetDateTime.now());
        p.setSignoffNotes(nullable(body, "signoffNotes"));
        placementRepository.save(p);
        return placementView(p);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listPlacements(UUID tenantId, String studentId) {
        StudentProfileEntity student = profile(tenantId, studentId);
        List<Map<String, Object>> items = placementRepository
                .findByTenantIdAndStudentProfileIdOrderByCreatedAtDesc(tenantId, student.getId())
                .stream().map(FundoRegistrationService::placementView).toList();
        return Map.of("studentId", studentId, "items", items);
    }

    @Transactional
    public Map<String, Object> graduate(UUID tenantId, String studentId, String actorId, Map<String, Object> body) {
        StudentProfileEntity student = profile(tenantId, studentId);
        GraduationRecordEntity g = new GraduationRecordEntity();
        g.setTenantId(tenantId);
        g.setStudentProfileId(student.getId());
        g.setProgramId(student.getProgramId());
        String title = nullable(body, "qualificationTitle");
        if (title == null) {
            title = programRepository.findByTenantIdAndId(tenantId, student.getProgramId())
                    .map(p -> p.getTitle()).orElse("Qualification");
        }
        g.setQualificationTitle(title);
        g.setRegistryNumber(str(body, "registryNumber", generateRegistryNumber()));
        g.setCertificateId(uuidOrNull(body.get("certificateId")));
        g.setStatus("CONFERRED");
        g.setCreatedBy(actorId);
        graduationRepository.save(g);

        student.setStatus("GRADUATED");
        student.setUpdatedAt(OffsetDateTime.now());
        profileRepository.save(student);
        return graduationView(g);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> academicRecord(UUID tenantId, String studentId) {
        StudentProfileEntity student = profile(tenantId, studentId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("studentId", student.getId().toString());
        out.put("studentNumber", student.getStudentNumber());
        out.put("status", student.getStatus());
        out.put("programId", student.getProgramId().toString());
        out.put("registrations", registrationRepository
                .findByTenantIdAndStudentProfileIdOrderByCreatedAtDesc(tenantId, student.getId())
                .stream().map(FundoRegistrationService::registrationView).toList());
        out.put("placements", placementRepository
                .findByTenantIdAndStudentProfileIdOrderByCreatedAtDesc(tenantId, student.getId())
                .stream().map(FundoRegistrationService::placementView).toList());
        out.put("graduation", graduationRepository
                .findByTenantIdAndStudentProfileIdOrderByCreatedAtDesc(tenantId, student.getId())
                .stream().map(FundoRegistrationService::graduationView).toList());
        return out;
    }

    private StudentProfileEntity profile(UUID tenantId, String studentId) {
        return profileRepository.findByTenantIdAndId(tenantId, UUID.fromString(studentId))
                .orElseThrow(() -> new IllegalArgumentException("student not found"));
    }

    private static String subjectType(StudentProfileEntity s) {
        return s.getSubjectType() != null && !s.getSubjectType().isBlank() ? s.getSubjectType() : "STUDENT";
    }

    private static String subjectId(StudentProfileEntity s) {
        return s.getSubjectId() != null && !s.getSubjectId().isBlank() ? s.getSubjectId() : s.getStudentNumber();
    }

    private static Map<String, Object> registrationView(CourseRegistrationEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId().toString());
        m.put("courseId", r.getCourseId().toString());
        m.put("termId", r.getTermId() == null ? null : r.getTermId().toString());
        m.put("enrolmentId", r.getEnrolmentId() == null ? null : r.getEnrolmentId().toString());
        m.put("status", r.getStatus());
        m.put("registeredAt", r.getRegisteredAt() == null ? null : r.getRegisteredAt().toString());
        return m;
    }

    private static Map<String, Object> placementView(ClinicalPlacementEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId().toString());
        m.put("title", p.getTitle());
        m.put("venueId", p.getVenueId() == null ? null : p.getVenueId().toString());
        m.put("preceptorFacilitatorId", p.getPreceptorFacilitatorId() == null ? null : p.getPreceptorFacilitatorId().toString());
        m.put("startsOn", p.getStartsOn() == null ? null : p.getStartsOn().toString());
        m.put("endsOn", p.getEndsOn() == null ? null : p.getEndsOn().toString());
        m.put("signoffStatus", p.getSignoffStatus());
        m.put("signedOffBy", p.getSignedOffBy());
        return m;
    }

    private static Map<String, Object> graduationView(GraduationRecordEntity g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId().toString());
        m.put("qualificationTitle", g.getQualificationTitle());
        m.put("registryNumber", g.getRegistryNumber());
        m.put("awardedAt", g.getAwardedAt() == null ? null : g.getAwardedAt().toString());
        m.put("certificateId", g.getCertificateId() == null ? null : g.getCertificateId().toString());
        m.put("status", g.getStatus());
        return m;
    }

    private static String generateRegistryNumber() {
        return "GRAD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static LocalDate dateOrNull(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        return LocalDate.parse(o.toString());
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

    private static String str(Map<String, Object> body, String key, String def) {
        Object v = body.get(key);
        return v == null || v.toString().isBlank() ? def : v.toString();
    }

    private static String nullable(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }
}
