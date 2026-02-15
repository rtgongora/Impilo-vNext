package zw.gov.mohcc.impilo.dags.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.dags.persistence.entity.AccessDecisionEntity;

@Repository
public interface AccessDecisionRepository extends JpaRepository<AccessDecisionEntity, Long> {

    List<AccessDecisionEntity> findByRequestId(Long requestId);
}
