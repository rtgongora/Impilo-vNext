package zw.gov.mohcc.impilo.participation.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.participation.persistence.entity.TestingCohortEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestingCohortRepository extends JpaRepository<TestingCohortEntity, UUID> {

    Optional<TestingCohortEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<TestingCohortEntity> findByTenantIdOrderByNameAsc(UUID tenantId);

    List<TestingCohortEntity> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);
}
