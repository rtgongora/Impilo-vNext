package zw.gov.mohcc.impilo.search.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.search.persistence.entity.OutboxEventEntity;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {

    List<OutboxEventEntity> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
