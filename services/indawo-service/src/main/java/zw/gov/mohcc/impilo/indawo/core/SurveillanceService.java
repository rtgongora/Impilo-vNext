package zw.gov.mohcc.impilo.indawo.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.indawo.domain.*;
import zw.gov.mohcc.impilo.indawo.repository.*;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public-health surveillance, outbreak, case-investigation and field-team
 * coordination (net-new). Indawo is the SoR for public-health PLACE intelligence.
 *
 * <p>All reads/writes are tenant-scoped. Mutations emit outbox events. No PII —
 * case references use pseudonymous CPID only (doctrine). Authz routes through the
 * existing ext_authz path; policies {@code INDAWO-MODE}, {@code INSPECTION}
 * (spec-only, track P).</p>
 */
@Service
public class SurveillanceService {

    private static final Logger log = LoggerFactory.getLogger(SurveillanceService.class);
    private static final String PRODUCER = "indawo-service";

    private final SurveillanceAlertRepository alertRepository;
    private final OutbreakRepository outbreakRepository;
    private final CaseInvestigationRepository caseRepository;
    private final FieldTeamRepository teamRepository;
    private final FieldTeamDeploymentRepository deploymentRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SurveillanceService(SurveillanceAlertRepository alertRepository,
                               OutbreakRepository outbreakRepository,
                               CaseInvestigationRepository caseRepository,
                               FieldTeamRepository teamRepository,
                               FieldTeamDeploymentRepository deploymentRepository,
                               OutboxEventRepository outboxRepository,
                               ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.outbreakRepository = outbreakRepository;
        this.caseRepository = caseRepository;
        this.teamRepository = teamRepository;
        this.deploymentRepository = deploymentRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    // ── Surveillance alerts ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SurveillanceAlertEntity> listAlerts(UUID tenantId, String status) {
        return status != null
                ? alertRepository.findByTenantIdAndStatusOrderByDetectedAtDesc(tenantId, status)
                : alertRepository.findByTenantIdOrderByDetectedAtDesc(tenantId);
    }

    @Transactional
    public SurveillanceAlertEntity createAlert(UUID tenantId, String actor, SurveillanceAlertEntity input) {
        input.setTenantId(tenantId);
        input.setCreatedBy(actor);
        input.setUpdatedBy(actor);
        SurveillanceAlertEntity saved = alertRepository.save(input);
        emit("SURVEILLANCE_ALERT", saved.getAlertId().toString(), "SURVEILLANCE_ALERT_RAISED",
                tenantId, Map.of("alertId", saved.getAlertId().toString(),
                        "signalType", saved.getSignalType(), "severity", saved.getSeverity()));
        return saved;
    }

    // ── Outbreaks ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OutbreakEntity> listOutbreaks(UUID tenantId, String status) {
        return status != null
                ? outbreakRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status)
                : outbreakRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public OutbreakEntity createOutbreak(UUID tenantId, String actor, OutbreakEntity input) {
        input.setTenantId(tenantId);
        input.setCreatedBy(actor);
        input.setUpdatedBy(actor);
        OutbreakEntity saved = outbreakRepository.save(input);
        emit("OUTBREAK", saved.getOutbreakId().toString(), "OUTBREAK_DECLARED",
                tenantId, Map.of("outbreakId", saved.getOutbreakId().toString(),
                        "conditionCode", saved.getConditionCode(), "status", saved.getStatus()));
        return saved;
    }

    /** Legal outbreak lifecycle statuses (V007 schema comment: SUSPECTED|CONFIRMED|CONTAINED|CLOSED). */
    private static final java.util.Set<String> OUTBREAK_STATUSES =
            java.util.Set.of("SUSPECTED", "CONFIRMED", "CONTAINED", "CLOSED");

    /** Allowed forward transitions. A CLOSED outbreak is terminal; no regression to an earlier stage. */
    private static final Map<String, java.util.Set<String>> OUTBREAK_TRANSITIONS = Map.of(
            "SUSPECTED", java.util.Set.of("CONFIRMED", "CLOSED"),
            "CONFIRMED", java.util.Set.of("CONTAINED", "CLOSED"),
            "CONTAINED", java.util.Set.of("CLOSED"),
            "CLOSED", java.util.Set.of());

    @Transactional
    public OutbreakEntity updateOutbreakStatus(UUID tenantId, UUID outbreakId, String status, String actor) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Outbreak status is required");
        }
        if (!OUTBREAK_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Unknown outbreak status: " + status);
        }
        OutbreakEntity o = outbreakRepository.findByTenantIdAndOutbreakId(tenantId, outbreakId)
                .orElseThrow(() -> new IllegalArgumentException("Outbreak not found: " + outbreakId));
        String current = o.getStatus();
        if (!status.equals(current) && !OUTBREAK_TRANSITIONS.getOrDefault(current, java.util.Set.of()).contains(status)) {
            throw new IllegalArgumentException(
                    "Illegal outbreak status transition: " + current + " -> " + status);
        }
        o.setStatus(status);
        o.setUpdatedBy(actor);
        if ("CONFIRMED".equals(status) && o.getDeclaredAt() == null) o.setDeclaredAt(OffsetDateTime.now());
        if ("CLOSED".equals(status)) o.setClosedAt(OffsetDateTime.now());
        OutbreakEntity saved = outbreakRepository.save(o);
        emit("OUTBREAK", outbreakId.toString(), "OUTBREAK_STATUS_CHANGED",
                tenantId, Map.of("outbreakId", outbreakId.toString(), "status", status));
        return saved;
    }

    // ── Case investigations ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CaseInvestigationEntity> listCases(UUID tenantId, UUID outbreakId) {
        return outbreakId != null
                ? caseRepository.findByTenantIdAndOutbreakIdOrderByCreatedAtDesc(tenantId, outbreakId)
                : caseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public CaseInvestigationEntity createCase(UUID tenantId, String actor, CaseInvestigationEntity input) {
        input.setTenantId(tenantId);
        input.setCreatedBy(actor);
        input.setUpdatedBy(actor);
        CaseInvestigationEntity saved = caseRepository.save(input);
        // Keep the parent outbreak case-count consistent.
        if (saved.getOutbreakId() != null) {
            outbreakRepository.findByTenantIdAndOutbreakId(tenantId, saved.getOutbreakId())
                    .ifPresent(o -> {
                        o.setCaseCount((int) caseRepository.countByTenantIdAndOutbreakId(tenantId, o.getOutbreakId()));
                        outbreakRepository.save(o);
                    });
        }
        emit("CASE_INVESTIGATION", saved.getCaseId().toString(), "CASE_INVESTIGATION_OPENED",
                tenantId, Map.of("caseId", saved.getCaseId().toString(),
                        "classification", saved.getClassification()));
        return saved;
    }

    // ── Field teams + deployments ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FieldTeamEntity> listTeams(UUID tenantId) {
        return teamRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public FieldTeamEntity createTeam(UUID tenantId, String actor, FieldTeamEntity input) {
        input.setTenantId(tenantId);
        input.setCreatedBy(actor);
        input.setUpdatedBy(actor);
        FieldTeamEntity saved = teamRepository.save(input);
        emit("FIELD_TEAM", saved.getTeamId().toString(), "FIELD_TEAM_CREATED",
                tenantId, Map.of("teamId", saved.getTeamId().toString(), "teamType", saved.getTeamType()));
        return saved;
    }

    @Transactional
    public FieldTeamDeploymentEntity deployTeam(UUID tenantId, String actor, UUID teamId,
                                                UUID outbreakId, UUID siteId, String objective) {
        FieldTeamEntity team = teamRepository.findByTenantIdAndTeamId(tenantId, teamId)
                .orElseThrow(() -> new IllegalArgumentException("Field team not found: " + teamId));

        // Guard against double-deploy: a team already DEPLOYED, or one that still has an
        // ACTIVE deployment row, cannot be deployed again (would corrupt team status).
        if ("DEPLOYED".equals(team.getStatus())
                || deploymentRepository.countByTenantIdAndTeamIdAndStatus(tenantId, teamId, "ACTIVE") > 0) {
            throw new IllegalStateException("Field team already deployed: " + teamId);
        }

        FieldTeamDeploymentEntity dep = new FieldTeamDeploymentEntity();
        dep.setTenantId(tenantId);
        dep.setTeamId(teamId);
        dep.setOutbreakId(outbreakId);
        dep.setSiteId(siteId);
        dep.setObjective(objective);
        dep.setCreatedBy(actor);
        dep.setUpdatedBy(actor);
        FieldTeamDeploymentEntity saved = deploymentRepository.save(dep);

        team.setStatus("DEPLOYED");
        team.setUpdatedBy(actor);
        teamRepository.save(team);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deploymentId", saved.getDeploymentId().toString());
        payload.put("teamId", teamId.toString());
        payload.put("outbreakId", outbreakId != null ? outbreakId.toString() : null);
        payload.put("siteId", siteId != null ? siteId.toString() : null);
        emit("FIELD_TEAM_DEPLOYMENT", saved.getDeploymentId().toString(), "FIELD_TEAM_DEPLOYED",
                tenantId, payload);
        log.info("Field team {} deployed (deployment {}) tenant {}", teamId, saved.getDeploymentId(), tenantId);
        return saved;
    }

    @Transactional
    public FieldTeamDeploymentEntity recallDeployment(UUID tenantId, UUID deploymentId, String actor) {
        FieldTeamDeploymentEntity dep = deploymentRepository.findByTenantIdAndDeploymentId(tenantId, deploymentId)
                .orElseThrow(() -> new IllegalArgumentException("Deployment not found: " + deploymentId));
        dep.setStatus("RECALLED");
        dep.setRecalledAt(OffsetDateTime.now());
        dep.setUpdatedBy(actor);
        FieldTeamDeploymentEntity saved = deploymentRepository.save(dep);

        // Only free the team when no other ACTIVE deployment remains (this one is now
        // RECALLED). Otherwise the team is still in the field on another deployment.
        if (deploymentRepository.countByTenantIdAndTeamIdAndStatus(tenantId, dep.getTeamId(), "ACTIVE") == 0) {
            teamRepository.findByTenantIdAndTeamId(tenantId, dep.getTeamId()).ifPresent(t -> {
                t.setStatus("AVAILABLE");
                t.setUpdatedBy(actor);
                teamRepository.save(t);
            });
        }
        emit("FIELD_TEAM_DEPLOYMENT", deploymentId.toString(), "FIELD_TEAM_RECALLED",
                tenantId, Map.of("deploymentId", deploymentId.toString()));
        return saved;
    }

    // ── Place-mode summary (Indawo cockpit context) ─────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> placeModeSummary(UUID tenantId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("openAlerts", alertRepository.countByTenantIdAndStatus(tenantId, "OPEN"));
        summary.put("activeOutbreaks", outbreakRepository.countByTenantIdAndStatusNot(tenantId, "CLOSED"));
        summary.put("teams", teamRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).size());
        return summary;
    }

    // ── Outbox helper ───────────────────────────────────────────────────────

    private void emit(String aggregateType, String aggregateId, String eventType,
                      UUID tenantId, Map<String, Object> payload) {
        OutboxEventEntity ev = new OutboxEventEntity();
        ev.setAggregateType(aggregateType);
        ev.setAggregateId(aggregateId);
        ev.setEventType(eventType);
        ev.setSchemaVersion("1");
        ev.setIdempotencyKey(eventType + ":" + aggregateId + ":" + UUID.randomUUID());
        ev.setProducer(PRODUCER);
        ev.setTenantId(tenantId);
        ev.setPodId("national-spine");
        ev.setSubjectId(aggregateId);
        ev.setSubjectType(aggregateType);
        ev.setOccurredAt(OffsetDateTime.now());
        ev.setPartitionKey(tenantId.toString());
        ev.setPayloadJson(toJson(payload));
        outboxRepository.save(ev);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
