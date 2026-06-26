package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyDrawdownEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubsidyDrawdownRepository extends JpaRepository<SubsidyDrawdownEntity, UUID> {

    Optional<SubsidyDrawdownEntity> findByEnrolmentIdAndReference(UUID enrolmentId, String reference);
}
