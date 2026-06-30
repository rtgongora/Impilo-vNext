package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msika.persistence.entity.StorefrontEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface StorefrontRepository extends JpaRepository<StorefrontEntity, String> {
    List<StorefrontEntity> findBySellerType(String sellerType);
    Optional<StorefrontEntity> findBySellerTypeAndSellerId(String sellerType, String sellerId);
}
