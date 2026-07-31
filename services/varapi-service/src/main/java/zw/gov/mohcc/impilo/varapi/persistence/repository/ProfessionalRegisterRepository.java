package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProfessionalRegisterEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfessionalRegisterRepository extends JpaRepository<ProfessionalRegisterEntity, Long> {
    List<ProfessionalRegisterEntity> findByTenantIdAndCouncilIdOrderByRegisterCodeAsc(UUID tenantId, Long councilId);

    /**
     * The reconcile lookup. Matching on {@code (tenant, council, register_code)} is what makes
     * materialisation idempotent, and it is why the code the materialiser composes must be the same
     * code the migration seeds used — see {@code RegisterMaterialisationService}.
     */
    Optional<ProfessionalRegisterEntity> findByTenantIdAndCouncilIdAndRegisterCode(
            UUID tenantId, Long councilId, String registerCode);
}
