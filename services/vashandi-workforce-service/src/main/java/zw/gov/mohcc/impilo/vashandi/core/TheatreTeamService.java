package zw.gov.mohcc.impilo.vashandi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.TheatreTeamEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.TheatreTeamMemberEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.TheatreTeamMemberRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.TheatreTeamRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assembles theatre case teams. Each member's covering shift is soft-validated
 * (accept-and-flag, mirroring the virtual-pool duty gate) — a missing shift is
 * flagged {@code shiftCoverage=NO_SHIFT} but never blocks assembly. A CONFIRMED
 * team is what inpatient evaluateReadiness consumes for the TEAM domain.
 */
@Service
public class TheatreTeamService {

    private static final Logger log = LoggerFactory.getLogger(TheatreTeamService.class);
    private static final List<String> ROSTERED_STATUSES = List.of("scheduled", "confirmed", "checked_in");

    private final TheatreTeamRepository teamRepository;
    private final TheatreTeamMemberRepository memberRepository;
    private final ShiftRepository shiftRepository;
    private final VashandiOutboxWriter outboxWriter;

    public TheatreTeamService(TheatreTeamRepository teamRepository,
                              TheatreTeamMemberRepository memberRepository,
                              ShiftRepository shiftRepository,
                              VashandiOutboxWriter outboxWriter) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.shiftRepository = shiftRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public Map<String, Object> assembleTeam(Map<String, Object> body) {
        UUID tenantId = tenantId();
        String sessionRef = str(body, "sessionRef");
        if (sessionRef == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionRef is required");
        }
        TheatreTeamEntity team = new TheatreTeamEntity();
        team.setTenantId(tenantId);
        team.setSessionRef(UUID.fromString(sessionRef));
        if (str(body, "orListItemRef") != null) {
            team.setOrListItemRef(UUID.fromString(str(body, "orListItemRef")));
        }
        team.setStatus(TheatreTeamEntity.STATUS_DRAFT);
        teamRepository.save(team);
        return envelope(team);
    }

    @Transactional
    public Map<String, Object> addMember(String teamId, Map<String, Object> body) {
        TheatreTeamEntity team = requireTeam(teamId);
        String role = str(body, "role");
        String profile = str(body, "workforceProfileId");
        if (role == null || profile == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role and workforceProfileId are required");
        }
        TheatreTeamMemberEntity member = new TheatreTeamMemberEntity();
        member.setTenantId(team.getTenantId());
        member.setTeamId(team.getId());
        member.setRole(role.toUpperCase());
        UUID profileId = UUID.fromString(profile);
        member.setWorkforceProfileId(profileId);
        if (str(body, "shiftId") != null) {
            member.setShiftId(UUID.fromString(str(body, "shiftId")));
        }
        member.setShiftCoverage(validateCoverage(team.getTenantId(), profileId));
        memberRepository.save(member);
        emit(team, "member", "assigned", Map.of(
                "teamId", team.getId().toString(),
                "role", member.getRole(),
                "workforceProfileId", profile,
                "shiftCoverage", member.getShiftCoverage()));
        return memberMap(member);
    }

    @Transactional
    public Map<String, Object> confirmTeam(String teamId) {
        TheatreTeamEntity team = requireTeam(teamId);
        List<TheatreTeamMemberEntity> members = memberRepository.findByTeamId(team.getId());
        if (members.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot confirm a team with no members");
        }
        members.forEach(m -> {
            m.setConfirmed(true);
            memberRepository.save(m);
        });
        team.setStatus(TheatreTeamEntity.STATUS_CONFIRMED);
        teamRepository.save(team);
        emit(team, "team", "confirmed", Map.of(
                "teamId", team.getId().toString(),
                "sessionRef", team.getSessionRef().toString(),
                "memberCount", members.size()));
        return envelope(team);
    }

    @Transactional
    public Map<String, Object> standDown(String teamId, Map<String, Object> body) {
        TheatreTeamEntity team = requireTeam(teamId);
        team.setStatus(TheatreTeamEntity.STATUS_STOOD_DOWN);
        teamRepository.save(team);
        emit(team, "team", "stood_down", Map.of(
                "teamId", team.getId().toString(),
                "reason", str(body, "reason")));
        return envelope(team);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTeam(String teamId) {
        return envelope(requireTeam(teamId));
    }

    private String validateCoverage(UUID tenantId, UUID profileId) {
        try {
            long shifts = shiftRepository.countByTenantIdAndWorkforceProfileIdAndStatusIn(
                    tenantId, profileId, ROSTERED_STATUSES);
            if (shifts == 0) {
                log.warn("Theatre team member {} has no covering shift — accepted and flagged NO_SHIFT", profileId);
                return TheatreTeamMemberEntity.COVERAGE_NO_SHIFT;
            }
            return TheatreTeamMemberEntity.COVERAGE_COVERED;
        } catch (Exception e) {
            log.warn("Shift-coverage validation skipped for {}: {}", profileId, e.getMessage());
            return TheatreTeamMemberEntity.COVERAGE_UNVALIDATED;
        }
    }

    private Map<String, Object> envelope(TheatreTeamEntity team) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", team.getId().toString());
        out.put("tenantId", team.getTenantId().toString());
        out.put("sessionRef", team.getSessionRef().toString());
        out.put("orListItemRef", team.getOrListItemRef() != null ? team.getOrListItemRef().toString() : null);
        out.put("status", team.getStatus());
        out.put("members", memberRepository.findByTeamId(team.getId()).stream().map(this::memberMap).toList());
        return out;
    }

    private Map<String, Object> memberMap(TheatreTeamMemberEntity m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", m.getId() != null ? m.getId().toString() : null);
        out.put("role", m.getRole());
        out.put("workforceProfileId", m.getWorkforceProfileId().toString());
        out.put("shiftId", m.getShiftId() != null ? m.getShiftId().toString() : null);
        out.put("confirmed", m.isConfirmed());
        out.put("shiftCoverage", m.getShiftCoverage());
        return out;
    }

    private void emit(TheatreTeamEntity team, String entity, String action, Map<String, Object> payload) {
        try {
            outboxWriter.publish(team.getTenantId(), "THEATRE_TEAM", team.getId().toString(),
                    "theatre_" + entity, action,
                    "vashandi:theatre_" + entity + ":" + action + ":" + team.getId(), payload);
        } catch (Exception e) {
            log.warn("Failed to emit theatre team event {}.{}: {}", entity, action, e.getMessage());
        }
    }

    private TheatreTeamEntity requireTeam(String teamId) {
        UUID id;
        try {
            id = UUID.fromString(teamId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid team id");
        }
        return teamRepository.findByTenantIdAndId(tenantId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Theatre team not found: " + teamId));
    }

    private UUID tenantId() {
        return TrustContextHolder.require().tenantId();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body != null ? body.get(key) : null;
        return v != null && !v.toString().isBlank() ? v.toString() : null;
    }
}
