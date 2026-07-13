package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.costa.domain.entity.FailedMoneyEventEntity;

import java.util.List;

public interface FailedMoneyEventRepository extends JpaRepository<FailedMoneyEventEntity, Long> {

    List<FailedMoneyEventEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<FailedMoneyEventEntity> findAllByOrderByCreatedAtDesc();
}
