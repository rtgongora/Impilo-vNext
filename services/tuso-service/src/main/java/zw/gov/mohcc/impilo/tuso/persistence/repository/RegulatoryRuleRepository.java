package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryRuleEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegulatoryRuleRepository extends JpaRepository<RegulatoryRuleEntity, UUID> {
    Optional<RegulatoryRuleEntity> findFirstByCodeAndStatusOrderByVersionDesc(String code, String status);
    Optional<RegulatoryRuleEntity> findFirstByCodeOrderByVersionDesc(String code);
    List<RegulatoryRuleEntity> findAllByOrderByCodeAscVersionDesc();
}
