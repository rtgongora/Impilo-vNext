package zw.gov.mohcc.impilo.simba.social.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.social.entity.SocialReactionEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialReactionRepository extends JpaRepository<SocialReactionEntity, Long> {

    Optional<SocialReactionEntity> findBySubjectTypeAndSubjectIdAndActorCpid(
            String subjectType, UUID subjectId, String actorCpid);

    long countBySubjectTypeAndSubjectId(String subjectType, UUID subjectId);
}
