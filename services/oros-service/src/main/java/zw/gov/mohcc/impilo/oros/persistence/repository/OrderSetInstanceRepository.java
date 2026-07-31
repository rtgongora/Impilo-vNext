package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderSetInstanceEntity;

import java.util.UUID;

public interface OrderSetInstanceRepository extends JpaRepository<OrderSetInstanceEntity, UUID> {}
