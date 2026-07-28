package zw.gov.mohcc.impilo.surgery.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalSpecialtyIndicationEntity;

import java.util.List;
import java.util.UUID;

public interface SurgicalSpecialtyIndicationRepository extends JpaRepository<SurgicalSpecialtyIndicationEntity, UUID> {

    List<SurgicalSpecialtyIndicationEntity> findByTenantIdAndSpecialtyOrderByIndicationCodeAsc(
            UUID tenantId, String specialty);
}
