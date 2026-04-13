package zw.gov.mohcc.impilo.procurement.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procurement.persistence.entity.PoLineEntity;

import java.util.List;
import java.util.UUID;

public interface PoLineRepository extends JpaRepository<PoLineEntity, UUID> {
    List<PoLineEntity> findByPoId(UUID poId);
}
