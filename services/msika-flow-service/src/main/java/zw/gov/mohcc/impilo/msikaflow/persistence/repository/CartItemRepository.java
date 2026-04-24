package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.CartItemEntity;

import java.util.List;

@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, String> {
    List<CartItemEntity> findByCartIdOrderByUpdatedAtDesc(String cartId);
    void deleteByCartIdAndMsikaCoreCode(String cartId, String msikaCoreCode);
}

