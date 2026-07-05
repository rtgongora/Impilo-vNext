package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.EventOutboxEntity;

import java.util.List;

public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    List<EventOutboxEntity> findTop100ByPublishedAtIsNullOrderByIdAsc();
}
