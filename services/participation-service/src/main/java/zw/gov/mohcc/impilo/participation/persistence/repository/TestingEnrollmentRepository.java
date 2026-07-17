package zw.gov.mohcc.impilo.participation.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.participation.persistence.entity.TestingEnrollmentEntity;

import java.util.UUID;

@Repository
public interface TestingEnrollmentRepository extends JpaRepository<TestingEnrollmentEntity, UUID> {

    long countByCohortId(UUID cohortId);

    boolean existsByCohortIdAndContributorRef(UUID cohortId, String contributorRef);
}
