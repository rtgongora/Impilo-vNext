package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.UserLearningProfileEntity;

public interface UserLearningProfileRepository extends JpaRepository<UserLearningProfileEntity, UUID> {

    Optional<UserLearningProfileEntity> findByTenantIdAndSubjectTypeAndSubjectId(
            UUID tenantId, String subjectType, String subjectId);
}
