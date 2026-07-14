package zw.gov.mohcc.impilo.scheduling.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.TheatreSessionEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TheatreSessionRepository extends JpaRepository<TheatreSessionEntity, UUID> {

    Optional<TheatreSessionEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<TheatreSessionEntity> findByTenantIdAndFacilityIdAndSessionDateOrderByStartTimeAsc(
            UUID tenantId, String facilityId, LocalDate sessionDate);

    List<TheatreSessionEntity> findByTenantIdAndSessionDateOrderByStartTimeAsc(UUID tenantId, LocalDate sessionDate);
}
