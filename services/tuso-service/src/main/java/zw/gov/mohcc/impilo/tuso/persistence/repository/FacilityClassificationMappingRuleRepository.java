package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityClassificationMappingRuleEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityClassificationMappingRuleRepository
        extends JpaRepository<FacilityClassificationMappingRuleEntity, UUID> {

    Optional<FacilityClassificationMappingRuleEntity> findByCodeAndVersion(String code, Integer version);

    List<FacilityClassificationMappingRuleEntity> findByStatusOrderByVersionDesc(String status);

    Optional<FacilityClassificationMappingRuleEntity> findFirstByStatusOrderByVersionDesc(String status);
}
