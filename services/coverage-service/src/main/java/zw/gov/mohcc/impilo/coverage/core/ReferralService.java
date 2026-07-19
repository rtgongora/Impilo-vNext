package zw.gov.mohcc.impilo.coverage.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.domain.ReferralEntity;
import zw.gov.mohcc.impilo.coverage.repository.ReferralRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Referral / gatekeeping (spec §16). Validates a referral is active, in-date and has visits
 * remaining; consuming a visit exhausts it when the permitted count is reached. Emergency care
 * must never be blocked by referral rules — callers pass {@code emergency=true} to short-circuit.
 */
@Service
public class ReferralService {

    private final ReferralRepository referralRepository;
    private final CoverageEventService eventService;

    public ReferralService(ReferralRepository referralRepository, CoverageEventService eventService) {
        this.referralRepository = referralRepository;
        this.eventService = eventService;
    }

    public record ReferralValidity(boolean valid, String reason, int visitsRemaining) {}

    @Transactional(readOnly = true)
    public List<ReferralEntity> forMember(UUID tenantId, String memberCpid) {
        return referralRepository.findByTenantIdAndMemberCpid(tenantId, memberCpid);
    }

    @Transactional(readOnly = true)
    public ReferralEntity get(UUID tenantId, UUID id) {
        return referralRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Referral not found: " + id));
    }

    @Transactional
    public ReferralEntity create(UUID tenantId, String podId, UUID coverageId, String memberCpid,
                                 String referringProviderId, String referredProviderId, String referredFacilityId,
                                 String clinicalReferralRef, String scope, String reason, Integer permittedVisits,
                                 LocalDate validTo, UUID correlationId) {
        if (memberCpid == null || memberCpid.isBlank()) {
            throw new IllegalArgumentException("memberCpid is required");
        }
        ReferralEntity r = ReferralEntity.create(tenantId, podId, coverageId, memberCpid);
        r.setReferringProviderId(referringProviderId);
        r.setReferredProviderId(referredProviderId);
        r.setReferredFacilityId(referredFacilityId);
        r.setClinicalReferralRef(clinicalReferralRef);
        r.setScope(scope);
        r.setReason(reason);
        if (permittedVisits != null && permittedVisits > 0) r.setPermittedVisits(permittedVisits);
        r.setValidTo(validTo);
        referralRepository.save(r);
        emit("created", r, correlationId);
        return r;
    }

    @Transactional(readOnly = true)
    public ReferralValidity validate(UUID tenantId, UUID referralId, boolean emergency) {
        if (emergency) return new ReferralValidity(true, "EMERGENCY_BYPASS", 0);
        ReferralEntity r = get(tenantId, referralId);
        LocalDate today = LocalDate.now();
        if (!"ACTIVE".equals(r.getStatus())) return new ReferralValidity(false, "REFERRAL_" + r.getStatus(), 0);
        if (r.getValidFrom() != null && today.isBefore(r.getValidFrom())) {
            return new ReferralValidity(false, "REFERRAL_NOT_YET_VALID", 0);
        }
        if (r.getValidTo() != null && today.isAfter(r.getValidTo())) {
            return new ReferralValidity(false, "REFERRAL_EXPIRED", 0);
        }
        int remaining = r.getPermittedVisits() - r.getVisitsUsed();
        if (remaining <= 0) return new ReferralValidity(false, "REFERRAL_EXHAUSTED", 0);
        return new ReferralValidity(true, "REFERRAL_VALID", remaining);
    }

    @Transactional
    public ReferralEntity consumeVisit(UUID tenantId, String podId, UUID referralId, UUID correlationId) {
        ReferralEntity r = get(tenantId, referralId);
        ReferralValidity v = validate(tenantId, referralId, false);
        if (!v.valid()) throw new IllegalStateException("Referral cannot be consumed: " + v.reason());
        r.setVisitsUsed(r.getVisitsUsed() + 1);
        if (r.getVisitsUsed() >= r.getPermittedVisits()) r.setStatus("EXHAUSTED");
        r.touch();
        referralRepository.save(r);
        emit("consumed", r, correlationId);
        return r;
    }

    private void emit(String action, ReferralEntity r, UUID correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("referral_id", r.getId().toString());
        payload.put("member_cpid", r.getMemberCpid());
        payload.put("status", r.getStatus());
        payload.put("visits_used", r.getVisitsUsed());
        payload.put("permitted_visits", r.getPermittedVisits());
        eventService.emitDomain("REFERRAL", r.getId().toString(), "coverage.referral." + action,
                correlationId, r.getTenantId(), r.getPodId(), r.getMemberCpid(), "REFERRAL", payload);
    }
}
