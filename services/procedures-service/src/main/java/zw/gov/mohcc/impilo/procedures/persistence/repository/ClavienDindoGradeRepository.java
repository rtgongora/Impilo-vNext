package zw.gov.mohcc.impilo.procedures.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procedures.persistence.entity.ClavienDindoGradeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClavienDindoGradeRepository extends JpaRepository<ClavienDindoGradeEntity, UUID> {
    Optional<ClavienDindoGradeEntity> findByTenantIdAndGradeCode(UUID tenantId, String gradeCode);
    List<ClavienDindoGradeEntity> findByTenantIdOrderByDisplayOrderAsc(UUID tenantId);
}
