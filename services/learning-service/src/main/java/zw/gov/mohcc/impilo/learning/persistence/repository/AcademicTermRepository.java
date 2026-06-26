package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.AcademicTermEntity;

public interface AcademicTermRepository extends JpaRepository<AcademicTermEntity, UUID> {

    Optional<AcademicTermEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<AcademicTermEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    List<AcademicTermEntity> findByTenantIdAndProgramIdOrderByCreatedAtDesc(UUID tenantId, UUID programId, Pageable pageable);
}
