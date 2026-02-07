package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.msika.persistence.entity.OrderableDetailEntity;

import java.util.List;

public interface OrderableDetailRepository extends JpaRepository<OrderableDetailEntity, String> {
    List<OrderableDetailEntity> findByOrderType(String orderType);
}
