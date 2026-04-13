package zw.gov.mohcc.impilo.procurement.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procurement.persistence.entity.GrnLineEntity;

import java.util.List;
import java.util.UUID;

public interface GrnLineRepository extends JpaRepository<GrnLineEntity, UUID> {
    List<GrnLineEntity> findByGrnId(UUID grnId);
}
