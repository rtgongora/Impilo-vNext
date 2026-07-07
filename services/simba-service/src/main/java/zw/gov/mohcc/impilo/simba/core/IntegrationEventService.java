package zw.gov.mohcc.impilo.simba.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.IntegrationEventEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.IntegrationEventRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Integration handoff ledger service. Simba owns none of stock/clinical/learning/comms/orders — when
 * a journey needs an owner, this records a PENDING (or UNSUPPORTED) ledger row so the handoff is
 * honest and auditable. The outbox event is the functional seam consumers subscribe to; this ledger
 * lets Simba track and (later) reconcile the handoff. It NEVER fakes a successful delivery.
 */
@Service
public class IntegrationEventService {

    private final IntegrationEventRepository repository;
    private final ObjectMapper objectMapper;

    public IntegrationEventService(IntegrationEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IntegrationEventEntity record(UUID tenantId, String personCpid, String targetOwner,
                                         String intent, String status, Map<String, Object> payload,
                                         String sourceRef) {
        IntegrationEventEntity e = new IntegrationEventEntity();
        e.setTenantId(tenantId);
        e.setPersonCpid(personCpid);
        e.setTargetOwner(targetOwner);
        e.setIntent(intent);
        e.setStatus(status == null ? "PENDING" : status);
        e.setSourceRef(sourceRef);
        if (payload != null && !payload.isEmpty()) {
            try {
                e.setRequestPayload(objectMapper.writeValueAsString(payload));
            } catch (Exception ignored) {
                e.setRequestPayload(null);
            }
        }
        return repository.save(e);
    }

    @Transactional(readOnly = true)
    public List<IntegrationEventEntity> forPerson(UUID tenantId, String personCpid) {
        return repository.findByTenantIdAndPersonCpidOrderByCreatedAtDesc(tenantId, personCpid);
    }
}
