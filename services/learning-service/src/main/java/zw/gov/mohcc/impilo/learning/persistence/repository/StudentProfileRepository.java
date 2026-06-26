package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.StudentProfileEntity;

public interface StudentProfileRepository extends JpaRepository<StudentProfileEntity, UUID> {

    Optional<StudentProfileEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<StudentProfileEntity> findByTenantIdAndStudentNumber(UUID tenantId, String studentNumber);

    List<StudentProfileEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    List<StudentProfileEntity> findByTenantIdAndProgramIdOrderByCreatedAtDesc(UUID tenantId, UUID programId, Pageable pageable);
}
