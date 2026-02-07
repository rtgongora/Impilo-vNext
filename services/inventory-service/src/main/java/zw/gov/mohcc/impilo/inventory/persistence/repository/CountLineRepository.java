package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CountLineEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CountLineRepository extends JpaRepository<CountLineEntity, UUID> {

    List<CountLineEntity> findBySessionId(UUID sessionId);

    List<CountLineEntity> findBySessionIdAndItemCode(UUID sessionId, String itemCode);
}
