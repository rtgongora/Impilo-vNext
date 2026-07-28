package zw.gov.mohcc.impilo.procedures.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.procedures.api.dto.ComplicationDtos.*;
import zw.gov.mohcc.impilo.procedures.persistence.entity.ComplicationProfileEntity;
import zw.gov.mohcc.impilo.procedures.persistence.repository.ClavienDindoGradeRepository;
import zw.gov.mohcc.impilo.procedures.persistence.repository.ComplicationClassRepository;
import zw.gov.mohcc.impilo.procedures.persistence.repository.ComplicationProfileRepository;

import java.util.List;
import java.util.UUID;

/**
 * Read access to Clavien-Dindo severity grades and complication profiles (pipeline §18).
 *
 * <p>Content, not engine — the same reason §9/§10/§15/§17 (safety pause, sedation, recovery,
 * aftercare) are content. What this deliberately does NOT do: record that a complication
 * actually occurred (an execution record, owned by whichever service performs the procedure —
 * inpatient-service today, the same boundary P8 drew for specimens and implants), grade a real
 * patient's real complication, or manage a complication pathway instance (surgery-service,
 * port 8396, not yet built — the ADR gives it that role). "Reopen-episode semantics" is a
 * separate, confirmed gap in inpatient-service's own episode state machine, not something this
 * service can close from here.</p>
 */
@Service
public class ComplicationProfileService {

    private static final String PUBLISHED = "PUBLISHED";

    private final ClavienDindoGradeRepository grades;
    private final ComplicationProfileRepository profiles;
    private final ComplicationClassRepository classes;

    public ComplicationProfileService(ClavienDindoGradeRepository grades,
                                      ComplicationProfileRepository profiles,
                                      ComplicationClassRepository classes) {
        this.grades = grades;
        this.profiles = profiles;
        this.classes = classes;
    }

    @Transactional(readOnly = true)
    public List<ClavienDindoGradeView> clavienDindoGrades(UUID tenantId) {
        return grades.findByTenantIdOrderByDisplayOrderAsc(tenantId).stream()
                .map(g -> new ClavienDindoGradeView(g.getGradeCode(), g.getGradeLabel(), g.getDescription()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ComplicationProfileView complicationProfile(UUID tenantId, String profileCode) {
        ComplicationProfileEntity p = profiles
                .findByTenantIdAndProfileCodeAndStatus(tenantId, profileCode, PUBLISHED)
                .orElseThrow(() -> new ProceduresDomainException(
                        "COMPLICATION_PROFILE_NOT_FOUND", 404,
                        "No published complication profile '" + profileCode + "'"));
        List<ComplicationClassView> classViews = classes
                .findByProfileIdOrderByDisplayOrderAsc(p.getId()).stream()
                .map(c -> new ComplicationClassView(c.getComplicationType(), c.getTypicalGradeCode(),
                        c.getMonitoringRequired(), c.getEscalationAction(), c.isNeverEvent()))
                .toList();
        return new ComplicationProfileView(p.getProfileCode(), p.getProfileName(),
                p.getApplicableSetting(), p.getDescription(), p.getStatus(),
                p.getApprovingAuthority(), p.getContentMaturity(), classViews);
    }
}
