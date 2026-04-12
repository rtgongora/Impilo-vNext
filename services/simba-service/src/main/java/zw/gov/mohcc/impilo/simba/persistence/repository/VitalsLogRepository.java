package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.VitalsLogEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface VitalsLogRepository extends JpaRepository<VitalsLogEntity, Long> {

    List<VitalsLogEntity> findByTenantIdAndPersonCpidOrderByRecordedAtDesc(UUID tenantId, String personCpid);
}
