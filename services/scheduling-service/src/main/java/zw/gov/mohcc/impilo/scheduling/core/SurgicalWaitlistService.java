package zw.gov.mohcc.impilo.scheduling.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.SurgicalWaitlistEntryEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.repository.SurgicalWaitlistRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the surgical waitlist. Placement is idempotent by referral_id (the
 * referral.surgical.accepted event may be redelivered). Escalation state is
 * recomputed at read — never persisted.
 */
@Service
public class SurgicalWaitlistService {

    private static final Logger log = LoggerFactory.getLogger(SurgicalWaitlistService.class);

    private final SurgicalWaitlistRepository repository;
    private final SchedulingOutboxPublisher outboxPublisher;

    public SurgicalWaitlistService(SurgicalWaitlistRepository repository,
                                   SchedulingOutboxPublisher outboxPublisher) {
        this.repository = repository;
        this.outboxPublisher = outboxPublisher;
    }

    /**
     * Idempotent placement. If an active entry already exists for the referral,
     * it is returned unchanged (safe under at-least-once event delivery).
     */
    @Transactional
    public Map<String, Object> placeOnWaitlist(UUID tenantId, UUID referralId, String patientId,
                                               String zibo, String clinicalPriority,
                                               Integer targetTimeframeDays, Map<String, Object> specialRequirements) {
        Optional<SurgicalWaitlistEntryEntity> existing = repository.findByTenantIdAndReferralId(tenantId, referralId)
                .stream()
                .filter(e -> !SurgicalWaitlistEntryEntity.STATUS_REMOVED.equals(e.getStatus())
                        && !SurgicalWaitlistEntryEntity.STATUS_CANCELLED.equals(e.getStatus()))
                .findFirst();
        if (existing.isPresent()) {
            log.info("Waitlist placement idempotent no-op for referral {} (already {})",
                    referralId, existing.get().getStatus());
            return existing.get().toEnvelope(LocalDate.now());
        }

        SurgicalWaitlistEntryEntity entry =
                new SurgicalWaitlistEntryEntity(UUID.randomUUID(), tenantId, referralId, patientId);
        entry.setZibo(zibo);
        entry.setClinicalPriority(clinicalPriority);
        if (targetTimeframeDays != null && targetTimeframeDays > 0) {
            entry.setTargetTimeframeDays(targetTimeframeDays);
        } else {
            entry.setTargetTimeframeDays(defaultTimeframe(clinicalPriority));
        }
        entry.setTargetDate(LocalDate.now().plusDays(entry.getTargetTimeframeDays()));
        if (specialRequirements != null) {
            entry.setSpecialRequirements(specialRequirements);
        }
        entry.appendHistory("PLACED", "referral.surgical.accepted", "listed from accepted surgical referral");
        repository.save(entry);
        emit("scheduling.waitlist.placed", entry);
        return entry.toEnvelope(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String status, String sort) {
        UUID tenantId = tenantId();
        List<SurgicalWaitlistEntryEntity> rows;
        if (status != null && !status.isBlank()) {
            rows = repository.findByTenantIdAndStatusOrderByClinicalPriorityAscTargetDateAsc(
                    tenantId, status.toUpperCase());
        } else {
            rows = repository.findByTenantIdOrderByClinicalPriorityAscTargetDateAsc(tenantId);
        }
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> out = rows.stream().map(e -> e.toEnvelope(today)).toList();
        if ("wait_days".equalsIgnoreCase(sort)) {
            out = out.stream()
                    .sorted((a, b) -> Long.compare((Long) b.get("waitDays"), (Long) a.get("waitDays")))
                    .toList();
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String id) {
        return require(id).toEnvelope(LocalDate.now());
    }

    @Transactional
    public Map<String, Object> defer(String id, Map<String, Object> body) {
        SurgicalWaitlistEntryEntity entry = require(id);
        entry.setStatus(SurgicalWaitlistEntryEntity.STATUS_DEFERRED);
        Object days = body != null ? body.get("deferDays") : null;
        if (days != null) {
            try {
                int add = Integer.parseInt(days.toString());
                entry.setTargetDate((entry.getTargetDate() != null ? entry.getTargetDate() : LocalDate.now())
                        .plusDays(add));
            } catch (NumberFormatException ignored) {
                // keep existing target
            }
        }
        entry.appendHistory("DEFERRED", actor(), str(body, "reason"));
        repository.save(entry);
        emit("scheduling.waitlist.returned", entry);
        return entry.toEnvelope(LocalDate.now());
    }

    /** Return a scheduled/deferred case to WAITING (e.g. after a cancelled OR item). */
    @Transactional
    public Map<String, Object> returnToWaiting(String id, Map<String, Object> body) {
        SurgicalWaitlistEntryEntity entry = require(id);
        entry.setStatus(SurgicalWaitlistEntryEntity.STATUS_WAITING);
        entry.setScheduledOrListItemId(null);
        entry.appendHistory("RETURNED", actor(), str(body, "reason"));
        repository.save(entry);
        emit("scheduling.waitlist.returned", entry);
        return entry.toEnvelope(LocalDate.now());
    }

    /** Mark scheduled — called when an OR list item is published for this entry. */
    @Transactional
    public void markScheduled(UUID tenantId, UUID waitlistEntryId, UUID orListItemId) {
        repository.findByTenantIdAndId(tenantId, waitlistEntryId).ifPresent(entry -> {
            entry.setStatus(SurgicalWaitlistEntryEntity.STATUS_SCHEDULED);
            entry.setScheduledOrListItemId(orListItemId);
            entry.appendHistory("SCHEDULED", "scheduler", "placed on OR list " + orListItemId);
            repository.save(entry);
            emit("scheduling.waitlist.scheduled", entry);
        });
    }

    /** Return the entry backing an OR item to WAITING when the item is cancelled. */
    @Transactional
    public void returnFromCancelledItem(UUID tenantId, UUID waitlistEntryId, String reason) {
        repository.findByTenantIdAndId(tenantId, waitlistEntryId).ifPresent(entry -> {
            entry.setStatus(SurgicalWaitlistEntryEntity.STATUS_WAITING);
            entry.setScheduledOrListItemId(null);
            entry.appendHistory("RETURNED", "scheduler", reason);
            repository.save(entry);
            emit("scheduling.waitlist.returned", entry);
        });
    }

    private int defaultTimeframe(String priority) {
        if (priority == null) {
            return 90;
        }
        return switch (priority.toUpperCase()) {
            case "P1" -> 7;
            case "P2" -> 30;
            case "P3" -> 90;
            default -> 180;
        };
    }

    private SurgicalWaitlistEntryEntity require(String id) {
        UUID entryId;
        try {
            entryId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid waitlist entry id");
        }
        return repository.findByTenantIdAndId(tenantId(), entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Waitlist entry not found: " + id));
    }

    private void emit(String eventType, SurgicalWaitlistEntryEntity entry) {
        LocalDate today = LocalDate.now();
        outboxPublisher.append("SurgicalWaitlistEntry", entry.getId().toString(), eventType, Map.of(
                "tenantId", entry.getTenantId().toString(),
                "waitlistEntryId", entry.getId().toString(),
                "referralId", entry.getReferralId().toString(),
                "patientId", entry.getPatientId(),
                "status", entry.getStatus(),
                "escalationState", entry.escalationState(today),
                "waitDays", entry.waitDays(today)));
    }

    private UUID tenantId() {
        return TrustContextHolder.require().tenantId();
    }

    private String actor() {
        try {
            String a = TrustContextHolder.require().actorId();
            return a != null ? a : "system";
        } catch (RuntimeException ex) {
            return "system";
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body != null ? body.get(key) : null;
        return v != null ? v.toString() : null;
    }
}
