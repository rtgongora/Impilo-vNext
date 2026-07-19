package zw.gov.mohcc.impilo.vashandi.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * PJ4/PJ16 end-to-end link (W5, D-P7): an APPROVED varapi FACILITY_ACCESS
 * request is materialised as a Vashandi assignment — which is what the
 * work-session lane (BFF /work-context/session) actually proves against.
 * Without this consumer an approval is a paper decision that never grants
 * anything.
 *
 * <p>Fail-closed and idempotent: only APPROVED + FACILITY_ACCESS events with a
 * person anchor and facility materialise; a missing workforce profile is
 * logged and skipped (no profile is invented here — profile creation is the
 * workforce-intake lane's job); an existing active assignment for the same
 * (profile, facility, engagement) is never duplicated.</p>
 */
@Component
public class FacilityAccessApprovedConsumer {

    private static final Logger log = LoggerFactory.getLogger(FacilityAccessApprovedConsumer.class);

    private static final List<String> LIVE_STATUSES = List.of("active", "approved");

    private final ObjectMapper objectMapper;
    private final WorkforceProfileRepository profileRepository;
    private final WorkforceAssignmentRepository assignmentRepository;

    public FacilityAccessApprovedConsumer(ObjectMapper objectMapper,
                                          WorkforceProfileRepository profileRepository,
                                          WorkforceAssignmentRepository assignmentRepository) {
        this.objectMapper = objectMapper;
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @KafkaListener(
            topics = {"varapi.events", "impilo.varapi.provider_access_request"},
            groupId = "vashandi-facility-access")
    @Transactional
    public void onAccessRequestEvent(String message) {
        try {
            if (message == null || message.isBlank()) {
                return;
            }
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.path("payload");
            if (!payload.isObject()) {
                payload = root;
            }
            String eventType = textOrEmpty(root, "event_type");
            boolean decided = eventType.contains("access_request.decided")
                    || payload.hasNonNull("requestType");
            if (!decided
                    || !"FACILITY_ACCESS".equalsIgnoreCase(textOrEmpty(payload, "requestType"))
                    || !"APPROVED".equalsIgnoreCase(textOrEmpty(payload, "status"))) {
                return;
            }

            String tenantIdText = firstNonBlank(
                    textOrNull(root, "tenant_id"), textOrNull(payload, "tenant_id"),
                    textOrNull(payload, "tenantId"));
            String healthId = textOrNull(payload, "applicantHealthId");
            String facilityIdText = textOrNull(payload, "facilityId");
            if (tenantIdText == null || healthId == null || facilityIdText == null) {
                log.warn("FACILITY_ACCESS approval without tenant/person/facility — not materialised: publicId={}",
                        textOrNull(payload, "publicId"));
                return;
            }
            UUID tenantId = UUID.fromString(tenantIdText);
            UUID facilityId = UUID.fromString(facilityIdText);

            WorkforceProfileEntity profile = profileRepository
                    .findByTenantIdAndHealthId(tenantId, healthId)
                    .orElse(null);
            if (profile == null) {
                // No profile is invented on a claim path — workforce intake owns
                // profile creation; the approval stays visible in varapi for
                // remediation.
                log.warn("FACILITY_ACCESS approved but no workforce profile for the person — not materialised: publicId={}",
                        textOrNull(payload, "publicId"));
                return;
            }

            String engagement = textOrEmpty(payload, "engagementType");
            boolean exists = assignmentRepository
                    .findByTenantIdAndFacilityIdAndStatus(tenantId, facilityId, "active").stream()
                    .anyMatch(a -> profile.getId().equals(a.getWorkforceProfileId())
                            && engagement.equalsIgnoreCase(a.getEngagementType() == null ? "" : a.getEngagementType()));
            if (exists) {
                log.info("FACILITY_ACCESS approval already materialised — skipped: publicId={}",
                        textOrNull(payload, "publicId"));
                return;
            }

            WorkforceAssignmentEntity assignment = new WorkforceAssignmentEntity();
            assignment.setTenantId(tenantId);
            assignment.setWorkforceProfileId(profile.getId());
            assignment.setAssignmentType("facility_access");
            assignment.setEngagementType(engagement.isBlank() ? null : engagement.toUpperCase(java.util.Locale.ROOT));
            assignment.setFacilityId(facilityId);
            assignment.setStatus("active");
            assignment.setEligibilityStatus("approved");
            assignment.setStartDate(parseDate(textOrNull(payload, "accessValidFrom"), LocalDate.now()));
            assignment.setEndDate(parseDate(textOrNull(payload, "accessValidTo"), null));
            assignment.setSourceAuthority("VARAPI_ACCESS_REQUEST:" + textOrEmpty(payload, "publicId"));
            assignment.setCreatedBy(textOrNull(payload, "decidedBy"));
            assignment.setApprovedBy(textOrNull(payload, "decidedBy"));
            assignmentRepository.save(assignment);

            log.info("FACILITY_ACCESS approval materialised as assignment {} (facility={}, engagement={}, publicId={})",
                    assignment.getId(), facilityId, engagement, textOrNull(payload, "publicId"));
        } catch (Exception e) {
            log.error("Failed to materialise FACILITY_ACCESS approval: {}", e.getMessage(), e);
        }
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String textOrEmpty(JsonNode node, String field) {
        String t = textOrNull(node, field);
        return t == null ? "" : t;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String v = node.get(field).asText();
        return v.isBlank() ? null : v;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
