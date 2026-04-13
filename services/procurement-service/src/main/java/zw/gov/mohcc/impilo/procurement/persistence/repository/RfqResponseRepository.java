package zw.gov.mohcc.impilo.procurement.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procurement.persistence.entity.RfqResponseEntity;

import java.util.List;
import java.util.UUID;

public interface RfqResponseRepository extends JpaRepository<RfqResponseEntity, UUID> {
    List<RfqResponseEntity> findByRfqId(UUID rfqId);
}
