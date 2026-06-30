package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EventOutboxEntity;

import java.util.List;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    @Query("select e from EventOutboxEntity e where e.publishedAt is null order by e.createdAt asc")
    List<EventOutboxEntity> findUnpublished(Pageable pageable);
}
