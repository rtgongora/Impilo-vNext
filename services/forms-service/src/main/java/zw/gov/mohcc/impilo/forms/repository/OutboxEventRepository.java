package zw.gov.mohcc.impilo.forms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.forms.domain.OutboxEventEntity;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {

    List<OutboxEventEntity> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
