package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.ExerciseSessionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExerciseSessionRepository extends JpaRepository<ExerciseSessionEntity, Long> {

    List<ExerciseSessionEntity> findByTenantIdAndPersonCpidOrderByStartTimeDesc(UUID tenantId, String personCpid);
}
