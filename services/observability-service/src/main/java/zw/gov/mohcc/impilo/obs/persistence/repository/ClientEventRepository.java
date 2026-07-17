package zw.gov.mohcc.impilo.obs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.obs.persistence.entity.ClientEventEntity;

@Repository
public interface ClientEventRepository extends JpaRepository<ClientEventEntity, Long> {
}
