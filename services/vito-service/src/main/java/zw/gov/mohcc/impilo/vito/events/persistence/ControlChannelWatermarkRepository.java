package zw.gov.mohcc.impilo.vito.events.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ControlChannelWatermarkRepository extends JpaRepository<ControlChannelWatermarkEntity, Long> {

    boolean existsByEventId(String eventId);
}
