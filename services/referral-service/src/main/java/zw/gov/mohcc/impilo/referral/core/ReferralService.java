package zw.gov.mohcc.impilo.referral.core;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.referral.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.referral.persistence.repository.ReferralRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReferralService {

    private final ReferralRepository referralRepository;
    private final ReferralOutboxPublisher outboxPublisher;

    public ReferralService(ReferralRepository referralRepository, ReferralOutboxPublisher outboxPublisher) {
        this.referralRepository = referralRepository;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listReferrals(String patientId) {
        UUID tenantId = tenantId();
        return referralRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId).stream()
                .map(ReferralEntity::toEnvelope)
                .toList();
    }

    @Transactional
    public Map<String, Object> createReferral(Map<String, Object> body) {
        Object patientId = body.get("patientId");
        if (patientId == null || patientId.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        UUID id = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>(body);
        ReferralEntity entity = new ReferralEntity(id, tenantId, patientId.toString(), payload);
        referralRepository.save(entity);
        emitLifecycle(entity, "REFERRAL_CREATED");
        return entity.toEnvelope();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getReferral(String id) {
        return require(id).toEnvelope();
    }

    @Transactional
    public Map<String, Object> acceptReferral(String id) {
        ReferralEntity entity = require(id);
        entity.accept();
        referralRepository.save(entity);
        emitLifecycle(entity, "REFERRAL_ACCEPTED");
        return entity.toEnvelope();
    }

    @Transactional
    public Map<String, Object> respondReferral(String id, Map<String, Object> body) {
        ReferralEntity entity = require(id);
        String notes = body != null && body.get("notes") != null ? body.get("notes").toString() : null;
        entity.respond(notes);
        referralRepository.save(entity);
        emitLifecycle(entity, "REFERRAL_RESPONDED");
        return entity.toEnvelope();
    }

    @Transactional
    public Map<String, Object> completeReferral(String id, Map<String, Object> body) {
        ReferralEntity entity = require(id);
        entity.complete();
        referralRepository.save(entity);
        emitLifecycle(entity, "REFERRAL_COMPLETED");
        return entity.toEnvelope();
    }

    private ReferralEntity require(String id) {
        UUID referralId;
        try {
            referralId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid referral id");
        }
        return referralRepository.findByTenantIdAndId(tenantId(), referralId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Referral not found: " + id));
    }

    private UUID tenantId() {
        return TrustContextHolder.require().tenantId();
    }

    private void emitLifecycle(ReferralEntity entity, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", entity.getTenantId().toString());
        payload.put("patientId", entity.getPatientId());
        payload.put("status", entity.getStatus());
        outboxPublisher.append("Referral", entity.getId().toString(), eventType, payload);
    }
}
