package zw.gov.mohcc.impilo.obs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.obs.persistence.entity.ServiceHeartbeatEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ServiceHeartbeatRepository extends JpaRepository<ServiceHeartbeatEntity, Long> {

    Optional<ServiceHeartbeatEntity> findByServiceNameAndInstanceId(String serviceName, String instanceId);

    List<ServiceHeartbeatEntity> findByServiceName(String serviceName);

    @Query("SELECT h FROM ServiceHeartbeatEntity h WHERE h.lastHeartbeat >= :since")
    List<ServiceHeartbeatEntity> findRecentHeartbeats(@Param("since") OffsetDateTime since);

    @Query("SELECT h FROM ServiceHeartbeatEntity h WHERE h.lastHeartbeat < :threshold")
    List<ServiceHeartbeatEntity> findStaleHeartbeats(@Param("threshold") OffsetDateTime threshold);
}
