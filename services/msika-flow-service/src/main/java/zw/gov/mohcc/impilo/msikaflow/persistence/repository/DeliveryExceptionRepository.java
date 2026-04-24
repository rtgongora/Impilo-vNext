package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.domain.ExceptionStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.DeliveryExceptionEntity;

import java.util.List;

@Repository
public interface DeliveryExceptionRepository extends JpaRepository<DeliveryExceptionEntity, String> {
    List<DeliveryExceptionEntity> findTop100ByStatusOrderByRaisedAtDesc(ExceptionStatus status);
    List<DeliveryExceptionEntity> findByPlanIdOrderByRaisedAtDesc(String planId);
}

