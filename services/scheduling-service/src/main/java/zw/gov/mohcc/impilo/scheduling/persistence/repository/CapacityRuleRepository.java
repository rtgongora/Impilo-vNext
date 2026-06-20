package zw.gov.mohcc.impilo.scheduling.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.CapacityRuleEntity;

import java.util.UUID;

public interface CapacityRuleRepository extends JpaRepository<CapacityRuleEntity, UUID> {
}
