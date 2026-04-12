package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.MoodLogEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface MoodLogRepository extends JpaRepository<MoodLogEntity, Long> {

    List<MoodLogEntity> findByTenantIdAndPersonCpidOrderByRecordedAtDesc(UUID tenantId, String personCpid);
}
