package zw.gov.mohcc.impilo.learning.fundo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.AcademicProgramEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.AcademicTermEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.AcademicProgramRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.AcademicTermRepository;

/** Academic program + term administration for pre-service education (A4). */
@Service
public class FundoAcademicService {

    private final AcademicProgramRepository programRepository;
    private final AcademicTermRepository termRepository;

    public FundoAcademicService(AcademicProgramRepository programRepository, AcademicTermRepository termRepository) {
        this.programRepository = programRepository;
        this.termRepository = termRepository;
    }

    @Transactional
    public Map<String, Object> createProgram(UUID tenantId, String actorId, Map<String, Object> body) {
        AcademicProgramEntity p = new AcademicProgramEntity();
        p.setTenantId(tenantId);
        p.setCode(reqStr(body, "code"));
        p.setTitle(reqStr(body, "title"));
        p.setQualificationLevel(nullable(body, "qualificationLevel"));
        if (body.get("durationTerms") != null) {
            p.setDurationTerms(Integer.valueOf(body.get("durationTerms").toString()));
        }
        p.setCadreTarget(nullable(body, "cadreTarget"));
        p.setStatus(str(body, "status", "ACTIVE"));
        p.setCreatedBy(actorId);
        programRepository.save(p);
        return programView(p);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listPrograms(UUID tenantId, int limit) {
        List<Map<String, Object>> items = programRepository
                .findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, limit))
                .stream().map(FundoAcademicService::programView).toList();
        return Map.of("items", items, "limit", limit);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProgram(UUID tenantId, String programId) {
        return programRepository.findByTenantIdAndId(tenantId, UUID.fromString(programId))
                .map(FundoAcademicService::programView)
                .orElseThrow(() -> new IllegalArgumentException("program not found"));
    }

    @Transactional
    public Map<String, Object> createTerm(UUID tenantId, String actorId, Map<String, Object> body) {
        AcademicTermEntity t = new AcademicTermEntity();
        t.setTenantId(tenantId);
        t.setProgramId(uuidOrNull(body.get("programId")));
        t.setName(reqStr(body, "name"));
        t.setAcademicYear(nullable(body, "academicYear"));
        if (body.get("termNo") != null) {
            t.setTermNo(Integer.valueOf(body.get("termNo").toString()));
        }
        t.setStartsOn(dateOrNull(body.get("startsOn")));
        t.setEndsOn(dateOrNull(body.get("endsOn")));
        t.setRegistrationOpensAt(timeOrNull(body.get("registrationOpensAt")));
        t.setRegistrationClosesAt(timeOrNull(body.get("registrationClosesAt")));
        t.setStatus(str(body, "status", "PLANNED"));
        t.setCreatedBy(actorId);
        termRepository.save(t);
        return termView(t);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listTerms(UUID tenantId, String programId, int limit) {
        List<AcademicTermEntity> rows = (programId == null || programId.isBlank())
                ? termRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, limit))
                : termRepository.findByTenantIdAndProgramIdOrderByCreatedAtDesc(tenantId, UUID.fromString(programId), PageRequest.of(0, limit));
        return Map.of("items", rows.stream().map(FundoAcademicService::termView).toList(), "limit", limit);
    }

    /** Whether a term's registration window is currently open. */
    @Transactional(readOnly = true)
    public boolean registrationOpen(UUID tenantId, UUID termId) {
        AcademicTermEntity t = termRepository.findByTenantIdAndId(tenantId, termId).orElse(null);
        if (t == null) return false;
        OffsetDateTime now = OffsetDateTime.now();
        boolean afterOpen = t.getRegistrationOpensAt() == null || !now.isBefore(t.getRegistrationOpensAt());
        boolean beforeClose = t.getRegistrationClosesAt() == null || now.isBefore(t.getRegistrationClosesAt());
        return afterOpen && beforeClose;
    }

    private static Map<String, Object> programView(AcademicProgramEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId().toString());
        m.put("code", p.getCode());
        m.put("title", p.getTitle());
        m.put("qualificationLevel", p.getQualificationLevel());
        m.put("durationTerms", p.getDurationTerms());
        m.put("cadreTarget", p.getCadreTarget());
        m.put("status", p.getStatus());
        return m;
    }

    private static Map<String, Object> termView(AcademicTermEntity t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId().toString());
        m.put("programId", t.getProgramId() == null ? null : t.getProgramId().toString());
        m.put("name", t.getName());
        m.put("academicYear", t.getAcademicYear());
        m.put("termNo", t.getTermNo());
        m.put("startsOn", t.getStartsOn() == null ? null : t.getStartsOn().toString());
        m.put("endsOn", t.getEndsOn() == null ? null : t.getEndsOn().toString());
        m.put("registrationOpensAt", t.getRegistrationOpensAt() == null ? null : t.getRegistrationOpensAt().toString());
        m.put("registrationClosesAt", t.getRegistrationClosesAt() == null ? null : t.getRegistrationClosesAt().toString());
        m.put("status", t.getStatus());
        return m;
    }

    private static LocalDate dateOrNull(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        return LocalDate.parse(o.toString());
    }

    private static OffsetDateTime timeOrNull(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        return OffsetDateTime.parse(o.toString());
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
