package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodReservationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodReservationRepository extends JpaRepository<BloodReservationEntity, Long> {
    Optional<BloodReservationEntity> findByReservationIdAndTenantId(UUID reservationId, UUID tenantId);
    List<BloodReservationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
