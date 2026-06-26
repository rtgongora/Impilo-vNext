package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.StudentApplicationEntity;

public interface StudentApplicationRepository extends JpaRepository<StudentApplicationEntity, UUID> {

    Optional<StudentApplicationEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<StudentApplicationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    List<StudentApplicationEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status, Pageable pageable);

    List<StudentApplicationEntity> findByTenantIdAndProgramIdOrderByCreatedAtDesc(UUID tenantId, UUID programId, Pageable pageable);
}
