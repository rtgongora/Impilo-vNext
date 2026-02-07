package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.CardStateTransitionEntity;

import java.util.List;

@Repository
public interface CardStateTransitionRepository extends JpaRepository<CardStateTransitionEntity, Long> {

    List<CardStateTransitionEntity> findByCardIdOrderByCreatedAtAsc(Long cardId);
}
