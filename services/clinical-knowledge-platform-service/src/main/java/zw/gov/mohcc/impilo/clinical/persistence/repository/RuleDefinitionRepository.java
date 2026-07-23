package zw.gov.mohcc.impilo.clinical.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.clinical.persistence.entity.RuleDefinitionEntity;

import java.util.List;

public interface RuleDefinitionRepository extends JpaRepository<RuleDefinitionEntity, Long> {

    List<RuleDefinitionEntity> findAll();
}
