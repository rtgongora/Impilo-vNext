package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.AcademicProgramEntity;

public interface AcademicProgramRepository extends JpaRepository<AcademicProgramEntity, UUID> {

    Optional<AcademicProgramEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<AcademicProgramEntity> findByTenantIdAndCode(UUID tenantId, String code);

    List<AcademicProgramEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
