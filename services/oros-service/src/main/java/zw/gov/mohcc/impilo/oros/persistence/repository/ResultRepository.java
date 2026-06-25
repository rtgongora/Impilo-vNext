package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.domain.ResultStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResultRepository extends JpaRepository<ResultEntity, UUID> {

    List<ResultEntity> findByOrderIdOrderByCreatedAtDesc(String orderId);

    /** Highest-version result of a given report status for an order (e.g. the current FINAL). */
    Optional<ResultEntity> findFirstByOrderIdAndReportStatusOrderByVersionDesc(
            String orderId, ResultStatus reportStatus);

    /** Highest-version result for an order, regardless of status (head of the version chain). */
    Optional<ResultEntity> findFirstByOrderIdOrderByVersionDesc(String orderId);
}
