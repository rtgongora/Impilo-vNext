package zw.gov.mohcc.impilo.telemonitoring.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.telemonitoring.events.TelemonitoringEventEmitter;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Programme-catalogue governance (OF-B21, Vol II §14.1): profiles are clinically governed
 * templates owned by the clinical-governance function.
 *
 * <p>Doctrine enforced here:</p>
 * <ul>
 *   <li><b>Versioned metadata</b> — every governed update bumps {@code version} and records
 *       the acting governor; plans reference {@code code} (+ version), never copy.</li>
 *   <li><b>Effective windows</b> — {@code effectiveFrom}/{@code effectiveTo} bound when a
 *       profile may seed new plans; a window never deletes anything.</li>
 *   <li><b>Retire never deletes</b> — retirement flips {@code retired=true, active=false}
 *       with actor + reason + timestamp; the row stays for referential and audit truth and
 *       existing plans continue under their referenced profile.</li>
 * </ul>
 */
@Service
public class MonitoringProgrammeService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringProgrammeService.class);

    private final MonitoringProgrammeRepository programmeRepository;
    private final TelemonitoringEventEmitter eventEmitter;

    public MonitoringProgrammeService(MonitoringProgrammeRepository programmeRepository,
                                      TelemonitoringEventEmitter eventEmitter) {
        this.programmeRepository = programmeRepository;
        this.eventEmitter = eventEmitter;
    }

    // ── Commands ──

    public record CreateProgrammeCommand(
            java.util.UUID tenantId,
            String code,
            String name,
            String description,
            String conditionCode,
            String defaultThresholdsJson,
            String metadataJson,
            OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveTo,
            String actor) {
    }

    public record UpdateProgrammeCommand(
            java.util.UUID tenantId,
            String name,
            String description,
            String conditionCode,
            String defaultThresholdsJson,
            String metadataJson,
            OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveTo,
            Boolean active,
            String actor) {
    }

    // ── Governance lifecycle ──

    @Transactional
    public MonitoringProgrammeEntity create(CreateProgrammeCommand cmd) {
        require(cmd.tenantId() != null, "tenantId is required");
        requireText(cmd.code(), "code is required");
        requireText(cmd.name(), "name is required");
        requireText(cmd.actor(), "an identified governance actor is required to create a programme");
        validateWindow(cmd.effectiveFrom(), cmd.effectiveTo());
        if (programmeRepository.findByCode(cmd.code()).isPresent()) {
            throw new TelemonitoringDomainException("TM_PROGRAMME_EXISTS", 409,
                    "Monitoring programme already exists: " + cmd.code()
                            + " (update or retire it — codes are never reused)");
        }
        MonitoringProgrammeEntity programme = new MonitoringProgrammeEntity();
        programme.setCode(cmd.code());
        programme.setName(cmd.name());
        programme.setDescription(cmd.description());
        programme.setConditionCode(cmd.conditionCode());
        if (hasText(cmd.defaultThresholdsJson())) {
            programme.setDefaultThresholds(cmd.defaultThresholdsJson());
        }
        if (hasText(cmd.metadataJson())) {
            programme.setMetadata(cmd.metadataJson());
        }
        programme.setEffectiveFrom(cmd.effectiveFrom());
        programme.setEffectiveTo(cmd.effectiveTo());
        programme.setVersion(1);
        programme.setUpdatedBy(cmd.actor());
        programme = programmeRepository.save(programme);

        emitProgrammeEvent("created", programme, cmd.actor(), null, cmd.tenantId());
        return programme;
    }

    /** Governed update: bumps the profile version; a retired programme is immutable. */
    @Transactional
    public MonitoringProgrammeEntity update(String code, UpdateProgrammeCommand cmd) {
        require(cmd.tenantId() != null, "tenantId is required");
        requireText(cmd.actor(), "an identified governance actor is required to update a programme");
        MonitoringProgrammeEntity programme = loadByCode(code);
        if (programme.isRetired()) {
            throw new TelemonitoringDomainException("TM_PROGRAMME_RETIRED", 409,
                    "Programme " + code + " is retired and immutable (retire never deletes; create a successor code)");
        }
        OffsetDateTime from = cmd.effectiveFrom() != null ? cmd.effectiveFrom() : programme.getEffectiveFrom();
        OffsetDateTime to = cmd.effectiveTo() != null ? cmd.effectiveTo() : programme.getEffectiveTo();
        validateWindow(from, to);

        if (hasText(cmd.name())) programme.setName(cmd.name());
        if (cmd.description() != null) programme.setDescription(cmd.description());
        if (cmd.conditionCode() != null) programme.setConditionCode(cmd.conditionCode());
        if (hasText(cmd.defaultThresholdsJson())) programme.setDefaultThresholds(cmd.defaultThresholdsJson());
        if (hasText(cmd.metadataJson())) programme.setMetadata(cmd.metadataJson());
        programme.setEffectiveFrom(from);
        programme.setEffectiveTo(to);
        if (cmd.active() != null) programme.setActive(cmd.active());
        programme.setVersion(programme.getVersion() + 1);
        programme.setUpdatedBy(cmd.actor());
        programme = programmeRepository.save(programme);

        emitProgrammeEvent("updated", programme, cmd.actor(), null, cmd.tenantId());
        log.info("Monitoring programme {} updated to governance version {} by {}",
                code, programme.getVersion(), cmd.actor());
        return programme;
    }

    /**
     * Retire = deactivate forever, delete never. The row (and every plan referencing it)
     * survives; only new-plan creation is refused from here on.
     */
    @Transactional
    public MonitoringProgrammeEntity retire(java.util.UUID tenantId, String code, String reason, String actor) {
        require(tenantId != null, "tenantId is required");
        requireText(reason, "A reason is required to retire a programme");
        requireText(actor, "an identified governance actor is required to retire a programme");
        MonitoringProgrammeEntity programme = loadByCode(code);
        if (programme.isRetired()) {
            throw new TelemonitoringDomainException("TM_PROGRAMME_RETIRED", 409,
                    "Programme " + code + " is already retired");
        }
        programme.setRetired(true);
        programme.setActive(false);
        programme.setRetiredAt(OffsetDateTime.now());
        programme.setRetiredBy(actor);
        programme.setRetiredReason(reason);
        programme.setVersion(programme.getVersion() + 1);
        programme.setUpdatedBy(actor);
        programme = programmeRepository.save(programme);

        emitProgrammeEvent("retired", programme, actor, reason, tenantId);
        log.info("Monitoring programme {} RETIRED by {} ({}) — row retained, never deleted", code, actor, reason);
        return programme;
    }

    // ── Internals ──

    private MonitoringProgrammeEntity loadByCode(String code) {
        return programmeRepository.findByCode(code)
                .orElseThrow(() -> new TelemonitoringDomainException("TM_UNKNOWN_PROGRAMME", 404,
                        "Unknown monitoring programme: " + code));
    }

    private void emitProgrammeEvent(String action, MonitoringProgrammeEntity programme,
                                    String actor, String reason, java.util.UUID tenantId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("programmeId", programme.getId().toString());
        payload.put("code", programme.getCode());
        payload.put("name", programme.getName());
        payload.put("version", programme.getVersion());
        payload.put("active", programme.isActive());
        payload.put("retired", programme.isRetired());
        payload.put("effectiveFrom", programme.getEffectiveFrom());
        payload.put("effectiveTo", programme.getEffectiveTo());
        payload.put("actor", actor);
        if (reason != null) {
            payload.put("reason", reason);
        }
        eventEmitter.emitProgrammeEvent(action, programme.getCode(), payload, tenantId);
    }

    private static void validateWindow(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && !to.isAfter(from)) {
            throw new TelemonitoringDomainException("TM_INVALID_EFFECTIVE_WINDOW", 400,
                    "effectiveTo must be after effectiveFrom");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new TelemonitoringDomainException("TM_INVALID_REQUEST", 400, message);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new TelemonitoringDomainException("TM_INVALID_REQUEST", 400, message);
        }
    }
}
