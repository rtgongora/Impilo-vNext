package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.msika.persistence.entity.ChargeableDetailEntity;

import java.util.Optional;

public interface ChargeableDetailRepository extends JpaRepository<ChargeableDetailEntity, String> {
    Optional<ChargeableDetailEntity> findByTariffCode(String tariffCode);
}
