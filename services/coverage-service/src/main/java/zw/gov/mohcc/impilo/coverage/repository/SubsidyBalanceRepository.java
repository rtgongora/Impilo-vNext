package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyBalanceEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubsidyBalanceRepository extends JpaRepository<SubsidyBalanceEntity, UUID> {

    Optional<SubsidyBalanceEntity> findByEnrolmentIdAndPeriodYear(UUID enrolmentId, int periodYear);
}
