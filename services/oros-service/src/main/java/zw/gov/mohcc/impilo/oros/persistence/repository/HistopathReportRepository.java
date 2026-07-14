package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.persistence.entity.HistopathReportEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HistopathReportRepository extends JpaRepository<HistopathReportEntity, UUID> {

    /** All histopath reports for an order, newest first. */
    List<HistopathReportEntity> findByOrderIdOrderByCreatedAtDesc(String orderId);

    /** Latest histopath report for an order (the working head). */
    Optional<HistopathReportEntity> findFirstByOrderIdOrderByCreatedAtDesc(String orderId);
}
