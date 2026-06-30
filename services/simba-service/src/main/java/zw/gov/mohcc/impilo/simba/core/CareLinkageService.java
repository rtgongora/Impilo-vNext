package zw.gov.mohcc.impilo.simba.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.CareLinkageEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.CareLinkageRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wellness-to-care linkage. When a wellness journey identifies risk (red flag, screening due,
 * risk threshold, coach escalation) Simba records a linkage and emits an event. The clinical
 * workflow and results stay with the owner (PCT / emergency / screening) — Simba does not own the
 * referral package, the encounter, or the result. The BFF orchestrates the actual PCT referral
 * call and stamps {@code externalRef} back.
 */
@Service
public class CareLinkageService {

    private final CareLinkageRepository repository;
    private final SimbaEventEmitter events;

    public CareLinkageService(CareLinkageRepository repository, SimbaEventEmitter events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<CareLinkageEntity> list(UUID tenantId, String personCpid) {
        return repository.findByTenantIdAndPersonCpidOrderByCreatedAtDesc(tenantId, personCpid);
    }

    @Transactional
    public CareLinkageEntity route(CareLinkageEntity incoming) {
        // Safety: an EMERGENCY severity always targets the EMERGENCY owner, never a routine queue.
        if ("EMERGENCY".equals(incoming.getSeverity())) {
            incoming.setTargetOwner("EMERGENCY");
        }
        CareLinkageEntity saved = repository.save(incoming);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("linkage_id", saved.getLinkageId().toString());
        payload.put("person_cpid", saved.getPersonCpid());
        payload.put("trigger", saved.getTrigger());
        payload.put("severity", saved.getSeverity());
        payload.put("target_owner", saved.getTargetOwner());
        events.emit(saved.getTenantId(), "WELLNESS_CARE_LINKAGE", saved.getLinkageId().toString(),
                "impilo.simba.wellness.care.routed.v1", saved.getPersonCpid(),
                "simba:care-linkage:" + saved.getLinkageId(), payload);
        return saved;
    }
}
