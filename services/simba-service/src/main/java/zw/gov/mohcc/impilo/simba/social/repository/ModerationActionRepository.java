package zw.gov.mohcc.impilo.simba.social.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.social.entity.ModerationActionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ModerationActionRepository extends JpaRepository<ModerationActionEntity, Long> {

    List<ModerationActionEntity> findBySubjectTypeAndSubjectIdOrderByIdDesc(String subjectType, UUID subjectId);
}
