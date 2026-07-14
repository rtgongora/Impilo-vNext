package zw.gov.mohcc.impilo.scheduling.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.ResourceReservationEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ResourceReservationRepository extends JpaRepository<ResourceReservationEntity, UUID> {

    List<ResourceReservationEntity> findByTenantIdAndResourceKindAndResourceRefAndReservationDateAndStatus(
            UUID tenantId, String resourceKind, String resourceRef, LocalDate reservationDate, String status);

    List<ResourceReservationEntity> findByTenantIdAndReservationDateAndStatus(
            UUID tenantId, LocalDate reservationDate, String status);

    List<ResourceReservationEntity> findBySessionId(UUID sessionId);
}
