package zw.gov.mohcc.impilo.search.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.search.domain.IndexDefinitionEntity;

import java.util.List;

@Repository
public interface IndexDefinitionRepository extends JpaRepository<IndexDefinitionEntity, String> {

    List<IndexDefinitionEntity> findByTenantId(String tenantId);
}
