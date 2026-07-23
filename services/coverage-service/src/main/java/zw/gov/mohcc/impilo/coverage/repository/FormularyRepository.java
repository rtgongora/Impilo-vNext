package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.coverage.domain.FormularyEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FormularyRepository extends JpaRepository<FormularyEntity, UUID> {

    List<FormularyEntity> findByTenantIdAndPlanVersionIdAndMedicationCodeAndCodingSystem(
            UUID tenantId, UUID planVersionId, String medicationCode, String codingSystem);

    List<FormularyEntity> findByTenantIdAndPlanVersionIdOrderByMedicationCodeAsc(
            UUID tenantId, UUID planVersionId);

    Optional<FormularyEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
