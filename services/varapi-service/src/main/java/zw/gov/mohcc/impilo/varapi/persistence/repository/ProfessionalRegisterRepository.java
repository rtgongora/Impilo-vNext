package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProfessionalRegisterEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProfessionalRegisterRepository extends JpaRepository<ProfessionalRegisterEntity, Long> {
    List<ProfessionalRegisterEntity> findByTenantIdAndCouncilIdOrderByRegisterCodeAsc(UUID tenantId, Long councilId);
}
