package zw.gov.mohcc.impilo.air.persistence.repository;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.air.persistence.entity.AiEventOutboxEntity;

@Repository
public interface AiEventOutboxRepository extends JpaRepository<AiEventOutboxEntity, Long> {

    @Query("select e from AiEventOutboxEntity e where e.publishedAt is null order by e.createdAt asc")
    List<AiEventOutboxEntity> findUnpublished(Pageable pageable);
}
