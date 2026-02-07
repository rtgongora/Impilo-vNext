package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.msika.persistence.entity.ProductDetailEntity;

import java.util.Optional;

public interface ProductDetailRepository extends JpaRepository<ProductDetailEntity, String> {
    Optional<ProductDetailEntity> findByBarcode(String barcode);
}
