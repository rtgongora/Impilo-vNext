package zw.gov.mohcc.impilo.tuso.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.PractitionerInChargeAssignmentEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.PractitionerInChargeAssignmentRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Mirrors VARAPI PIC assignment lifecycle events into TUSO's facility regulatory view.
 *
 * VARAPI remains the source of truth for provider legitimacy and PIC assignment lifecycle.
 * TUSO stores a facility-scoped mirror for operational facility screens and downstream
 * facility legitimacy checks.
 */
@Component
public class VarapiPicAssignmentConsumer {

    private static final Logger log = LoggerFactory.getLogger(VarapiPicAssignmentConsumer.class);

    private final ObjectMapper objectMapper;
    private final FacilityRepository facilityRepository;
    private final PractitionerInChargeAssignmentRepository picRepository;

    public VarapiPicAssignmentConsumer(ObjectMapper objectMapper,
                                       FacilityRepository facilityRepository,
                                       PractitionerInChargeAssignmentRepository picRepository) {
        this.objectMapper = objectMapper;
        this.facilityRepository = facilityRepository;
        this.picRepository = picRepository;
    }

    @KafkaListener(topics = "impilo.varapi.pic_assignment", groupId = "tuso-service")
    @Transactional
    public void onPicAssignment(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.path("payload");
            if (payload.isMissingNode() || payload.isNull()) {
                payload = root;
            }

            long facilityId = payload.path("facilityId").asLong(0);
            long providerId = payload.path("providerId").asLong(0);
            long assignmentId = payload.path("assignmentId").asLong(0);
            if (facilityId <= 0 || providerId <= 0 || assignmentId <= 0) {
                log.warn("TUSO PIC mirror skip: missing facility/provider/assignment id");
                return;
            }

            FacilityEntity facility = facilityRepository.findById(facilityId)
                    .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));

            // Idempotent upsert on (facility_id, external_assignment_id).
            Optional<PractitionerInChargeAssignmentEntity> existing =
                    picRepository.findByFacilityIdAndExternalAssignmentId(facilityId, assignmentId);

            PractitionerInChargeAssignmentEntity entity = existing.orElseGet(PractitionerInChargeAssignmentEntity::new);
            entity.setFacility(facility);
            entity.setExternalAssignmentId(assignmentId);
            String publicId = payload.path("providerPublicId").asText(null);
            entity.setProviderPublicId(
                    publicId != null && !publicId.isBlank() ? publicId : "varapi:" + providerId);
            if (payload.hasNonNull("impiloHealthId")) {
                try {
                    entity.setImpiloHealthId(UUID.fromString(payload.get("impiloHealthId").asText()));
                } catch (Exception ex) {
                    log.debug("TUSO PIC mirror: could not parse impiloHealthId: {}", ex.getMessage());
                }
            }
            entity.setRole("PRACTITIONER_IN_CHARGE");
            if (entity.getStartDate() == null) entity.setStartDate(LocalDate.now());

            String approvalState = payload.path("approvalState").asText(null);
            if (approvalState != null && !approvalState.isBlank()) {
                entity.setApprovalState(approvalState);
            }

            String status = payload.path("status").asText(null);
            if ("TERMINATED".equalsIgnoreCase(status)) {
                entity.setEndDate(LocalDate.now());
            }

            picRepository.save(entity);
        } catch (Exception e) {
            log.error("TUSO PIC mirror failed: {}", e.getMessage(), e);
        }
    }
}

