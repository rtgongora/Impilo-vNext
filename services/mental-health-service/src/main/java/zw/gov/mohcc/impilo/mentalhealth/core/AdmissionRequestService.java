package zw.gov.mohcc.impilo.mentalhealth.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.mentalhealth.events.OutboxEventEntity;
import zw.gov.mohcc.impilo.mentalhealth.events.OutboxEventRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.AdmissionRequestEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.AdmissionRequestRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.ReferralRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Raises admission to PCT; never decides it. This service records the ASK — {@code pctAdmissionId}
 * is set only after PCT's own admission-handshake records a decision, via
 * {@link #acknowledge}, called back once that decision exists. Per CC-2, the admission decision
 * stays PCT's alone; nothing here mints, approves, or denies an admission.
 */
@Service
public class AdmissionRequestService {

    private static final Logger log = LoggerFactory.getLogger(AdmissionRequestService.class);

    private final ReferralRepository referralRepository;
    private final AdmissionRequestRepository repository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AdmissionRequestService(ReferralRepository referralRepository,
                                   AdmissionRequestRepository repository,
                                   OutboxEventRepository outboxRepository,
                                   ObjectMapper objectMapper) {
        this.referralRepository = referralRepository;
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AdmissionRequestEntity raise(UUID tenantId, UUID referralId, String reason, String requestedBy,
                                        UUID targetFacilityId) {
        ReferralEntity referral = referralRepository.findByIdAndTenantId(referralId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Mental health referral not found in this tenant: " + referralId));
        require(reason != null && !reason.isBlank(), "reason is required");
        require(requestedBy != null && !requestedBy.isBlank(), "requestedBy is required");

        AdmissionRequestEntity r = new AdmissionRequestEntity();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setReferralId(referralId);
        r.setReason(reason);
        r.setRequestedBy(requestedBy);
        r.setRequestedAt(OffsetDateTime.now());
        r.setTargetFacilityId(targetFacilityId);
        r.setStatus("RAISED");
        r.setCreatedAt(OffsetDateTime.now());
        AdmissionRequestEntity saved = repository.save(r);
        emit(saved, "MH_ADMISSION_REQUEST_RAISED");
        return saved;
    }

    /** PCT has recorded its own admission decision — this only records the correlation, never the decision. */
    @Transactional
    public AdmissionRequestEntity acknowledge(UUID id, UUID tenantId, String pctAdmissionId) {
        AdmissionRequestEntity r = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Mental health admission request not found in this tenant: " + id));
        require(pctAdmissionId != null && !pctAdmissionId.isBlank(), "pctAdmissionId is required to acknowledge");
        r.setStatus("ACKNOWLEDGED");
        r.setPctAdmissionId(pctAdmissionId);
        return repository.save(r);
    }

    public List<AdmissionRequestEntity> history(UUID tenantId, UUID referralId) {
        return repository.findByTenantIdAndReferralIdOrderByRequestedAtDesc(tenantId, referralId);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private void emit(AdmissionRequestEntity r, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("admissionRequestId", r.getId().toString());
        payload.put("referralId", r.getReferralId().toString());
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxRepository.save(OutboxEventEntity.create(
                    "MH_ADMISSION_REQUEST", r.getId().toString(), eventType, null,
                    r.getTenantId(), "national-spine", r.getId().toString(), "MH_ADMISSION_REQUEST", json));
        } catch (JsonProcessingException ex) {
            log.error("Admission request event payload could not be serialised: {}", ex.getMessage());
        }
    }
}
