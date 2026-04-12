package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.ActivityEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityRepository extends JpaRepository<ActivityEntity, Long> {

    List<ActivityEntity> findByTenantIdAndPersonCpidOrderByRecordedAtDesc(UUID tenantId, String personCpid);

    List<ActivityEntity> findByTenantIdAndPersonCpidAndActivityTypeOrderByRecordedAtDesc(
            UUID tenantId, String personCpid, String activityType);
}
