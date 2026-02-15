package zw.gov.mohcc.impilo.ia.core;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ia.persistence.entity.AttestationEntity;
import zw.gov.mohcc.impilo.ia.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ia.persistence.repository.AttestationRepository;
import zw.gov.mohcc.impilo.ia.persistence.repository.EventOutboxRepository;

@Service
public class AttestationService {

    private final AttestationRepository attestationRepository;
    private final EventOutboxRepository outboxRepository;

    public AttestationService(AttestationRepository attestationRepository,
                              EventOutboxRepository outboxRepository) {
        this.attestationRepository = attestationRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public AttestationEntity recordAttestation(UUID tenantId, String actorId, UUID correlationId,
                                               AttestationType attestationType, String deviceFingerprint,
                                               String evidence, AttestationOutcome outcome,
                                               BigDecimal confidence) {
        AttestationEntity entity = new AttestationEntity();
        entity.setTenantId(tenantId);
        entity.setActorId(actorId);
        entity.setAttestationType(attestationType);
        entity.setDeviceFingerprint(deviceFingerprint);
        entity.setEvidence(evidence != null ? evidence : "{}");
        entity.setOutcome(outcome != null ? outcome : AttestationOutcome.PENDING);
        entity.setConfidence(confidence != null ? confidence : BigDecimal.ZERO);
        entity.setRecordedBy(actorId);
        entity = attestationRepository.save(entity);

        publishEvent("ATTESTATION", entity.getId().toString(), "ATTESTATION_RECORDED",
                buildAttestationPayload(entity), tenantId.toString(), correlationId);

        return entity;
    }

    @Transactional(readOnly = true)
    public List<AttestationEntity> listAttestations(UUID tenantId) {
        return attestationRepository.findByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public List<AttestationEntity> findByActor(UUID tenantId, String actorId) {
        return attestationRepository.findByTenantIdAndActorId(tenantId, actorId);
    }

    @Transactional(readOnly = true)
    public List<AttestationEntity> findByType(UUID tenantId, AttestationType type) {
        return attestationRepository.findByTenantIdAndAttestationType(tenantId, type);
    }

    private void publishEvent(String aggregateType, String aggregateId,
                              String eventType, String payload,
                              String tenantId, UUID correlationId) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setTenantId(tenantId);
        event.setCorrelationId(correlationId != null ? correlationId.toString() : null);
        outboxRepository.save(event);
    }

    private String buildAttestationPayload(AttestationEntity a) {
        return "{\"id\":" + a.getId()
                + ",\"tenantId\":\"" + a.getTenantId() + "\""
                + ",\"actorId\":\"" + a.getActorId() + "\""
                + ",\"attestationType\":\"" + a.getAttestationType() + "\""
                + ",\"outcome\":\"" + a.getOutcome() + "\""
                + ",\"confidence\":" + a.getConfidence()
                + "}";
    }
}
