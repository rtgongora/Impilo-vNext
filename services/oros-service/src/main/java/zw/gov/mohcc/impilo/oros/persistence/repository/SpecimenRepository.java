package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.domain.SpecimenStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.SpecimenEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpecimenRepository extends JpaRepository<SpecimenEntity, UUID> {

    /** Specimens for an order, newest first. */
    List<SpecimenEntity> findByOrderIdOrderByCreatedAtDesc(String orderId);

    /** Specimens in a tenant at a given status (e.g. awaiting receipt at the lab). */
    List<SpecimenEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, SpecimenStatus status);
}
