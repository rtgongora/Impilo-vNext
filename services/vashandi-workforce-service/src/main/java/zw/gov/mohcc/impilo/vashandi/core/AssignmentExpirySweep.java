package zw.gov.mohcc.impilo.vashandi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enforces assignment expiry (V008, D-P7): an {@code active} assignment whose
 * {@code endDate} has passed flips to {@code ended} and emits
 * {@code impilo.vashandi.assignment.ended.v1} — carrying the person anchor and
 * facility so the trust plane (tshepo-identity) can tear down the matching
 * WORK_CONTEXT tokens. A rotation/locum whose window lapses loses Work access
 * without anyone remembering to click revoke. Mirrors the varapi
 * {@code LicenceRenewalSweep} pattern.
 */
@Component
public class AssignmentExpirySweep {

    private static final Logger log = LoggerFactory.getLogger(AssignmentExpirySweep.class);

    static final String STATUS_ACTIVE = "active";
    static final String STATUS_ENDED = "ended";

    private final WorkforceAssignmentRepository assignmentRepository;
    private final WorkforceProfileRepository profileRepository;
    private final VashandiOutboxWriter outboxWriter;

    public AssignmentExpirySweep(WorkforceAssignmentRepository assignmentRepository,
                                 WorkforceProfileRepository profileRepository,
                                 VashandiOutboxWriter outboxWriter) {
        this.assignmentRepository = assignmentRepository;
        this.profileRepository = profileRepository;
        this.outboxWriter = outboxWriter;
    }

    @Scheduled(fixedDelayString = "${vashandi.assignment-sweep.interval-ms:3600000}")
    public void sweep() {
        int ended = endLapsed(LocalDate.now());
        if (ended > 0) {
            log.info("Assignment expiry sweep: {} ended", ended);
        }
    }

    /** Testable unit: active assignments with endDate strictly before {@code asOf} → ended. */
    @Transactional
    public int endLapsed(LocalDate asOf) {
        List<WorkforceAssignmentEntity> lapsed =
                assignmentRepository.findByStatusAndEndDateBefore(STATUS_ACTIVE, asOf);
        for (WorkforceAssignmentEntity assignment : lapsed) {
            assignment.setStatus(STATUS_ENDED);
            assignmentRepository.save(assignment);
            publishEnded(assignment);
        }
        return lapsed.size();
    }

    private void publishEnded(WorkforceAssignmentEntity assignment) {
        String healthId = profileRepository.findById(assignment.getWorkforceProfileId())
                .map(WorkforceProfileEntity::getHealthId)
                .orElse(null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignmentId", assignment.getId().toString());
        payload.put("workforceProfileId", assignment.getWorkforceProfileId().toString());
        // Person anchor for the trust plane's token teardown; may be absent for
        // profiles without a linked person — consumers must skip, never guess.
        payload.put("impiloHealthId", healthId != null ? healthId : "");
        payload.put("facilityId", assignment.getFacilityId() != null
                ? assignment.getFacilityId().toString() : "");
        payload.put("workspaceId", assignment.getWorkspaceId() != null
                ? assignment.getWorkspaceId().toString() : "");
        payload.put("engagementType", assignment.getEngagementType());
        payload.put("status", assignment.getStatus());
        payload.put("endDate", String.valueOf(assignment.getEndDate()));

        try {
            outboxWriter.publish(
                    assignment.getTenantId(),
                    "WORKFORCE_ASSIGNMENT",
                    assignment.getId().toString(),
                    "assignment",
                    "ended",
                    "assignment-ended:" + assignment.getId(),
                    payload);
        } catch (Exception e) {
            // Outbox write shares the sweep transaction; a serialisation failure
            // must fail the flip so the event is never silently lost.
            throw new IllegalStateException("Failed to publish assignment.ended for "
                    + assignment.getId(), e);
        }
    }
}
