package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ConfigDefinitionTypeEntity;

import java.util.List;

public interface ConfigDefinitionTypeRepository extends JpaRepository<ConfigDefinitionTypeEntity, String> {

    List<ConfigDefinitionTypeEntity> findAllByOrderBySortOrderAsc();
}
