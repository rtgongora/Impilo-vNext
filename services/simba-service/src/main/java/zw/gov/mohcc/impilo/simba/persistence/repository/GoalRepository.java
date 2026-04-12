package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.GoalEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalRepository extends JpaRepository<GoalEntity, Long> {

    List<GoalEntity> findByTenantIdAndPersonCpidOrderByCreatedAtDesc(UUID tenantId, String personCpid);

    Optional<GoalEntity> findByIdAndTenantId(Long id, UUID tenantId);
}
