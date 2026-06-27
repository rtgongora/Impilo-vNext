package zw.gov.mohcc.impilo.oros.core;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Allocates monotonically increasing accession sequence numbers per
 * (tenant, facility, year), backed by {@code oros_accession_counter}.
 *
 * <p>Uses a single atomic {@code INSERT … ON CONFLICT … DO UPDATE … RETURNING} so concurrent
 * reservations never collide (the accession-number uniqueness invariant). Runs in its own
 * transaction so the counter advance is durable independent of the surrounding order write.</p>
 */
@Component
public class AccessionSequenceAllocator {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long nextSequence(UUID tenantId, UUID facilityId, int year) {
        Object raw = entityManager.createNativeQuery(
                        "INSERT INTO oros_accession_counter (tenant_id, facility_id, seq_year, last_seq, updated_at) "
                                + "VALUES (:t, :f, :y, 1, now()) "
                                + "ON CONFLICT (tenant_id, facility_id, seq_year) "
                                + "DO UPDATE SET last_seq = oros_accession_counter.last_seq + 1, updated_at = now() "
                                + "RETURNING last_seq")
                .setParameter("t", tenantId)
                .setParameter("f", facilityId)
                .setParameter("y", year)
                .getSingleResult();
        return ((Number) raw).longValue();
    }
}
