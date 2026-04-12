package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.SleepSegmentEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface SleepSegmentRepository extends JpaRepository<SleepSegmentEntity, Long> {

    List<SleepSegmentEntity> findByTenantIdAndPersonCpidOrderByStartTimeDesc(UUID tenantId, String personCpid);
}
