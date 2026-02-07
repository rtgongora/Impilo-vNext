package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CpdEventEntity;

import java.util.List;

@Repository
public interface CpdEventRepository extends JpaRepository<CpdEventEntity, Long> {

    List<CpdEventEntity> findByCycleId(Long cycleId);

    List<CpdEventEntity> findByProviderIdOrderByEventDateDesc(Long providerId);
}
