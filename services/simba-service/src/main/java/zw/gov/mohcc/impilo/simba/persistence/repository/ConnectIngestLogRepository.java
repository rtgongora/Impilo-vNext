package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.ConnectIngestLogEntity;

import java.util.UUID;

@Repository
public interface ConnectIngestLogRepository extends JpaRepository<ConnectIngestLogEntity, Long> {

    boolean existsByTenantIdAndPersonCpidAndDataTypeAndSourceId(
            UUID tenantId, String personCpid, String dataType, String sourceId);
}
