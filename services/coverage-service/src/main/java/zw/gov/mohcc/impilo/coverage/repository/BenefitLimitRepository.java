package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.BenefitLimitEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface BenefitLimitRepository extends JpaRepository<BenefitLimitEntity, UUID> {
    List<BenefitLimitEntity> findByBenefitId(UUID benefitId);
}
