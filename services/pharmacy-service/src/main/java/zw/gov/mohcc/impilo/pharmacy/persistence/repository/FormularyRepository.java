package zw.gov.mohcc.impilo.pharmacy.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.FormularyEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FormularyRepository extends JpaRepository<FormularyEntity, UUID> {

    Optional<FormularyEntity> findByTenantIdAndFacilityIdAndDrugCode(UUID tenantId, UUID facilityId, String drugCode);

    Optional<FormularyEntity> findByTenantIdAndFacilityIdIsNullAndDrugCode(UUID tenantId, String drugCode);

    List<FormularyEntity> findByTenantIdAndAllowedTrue(UUID tenantId);
}
