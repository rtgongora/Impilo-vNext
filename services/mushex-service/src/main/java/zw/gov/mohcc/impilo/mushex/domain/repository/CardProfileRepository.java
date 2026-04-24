package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.CardProfileEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CardProfileRepository extends JpaRepository<CardProfileEntity, String> {

    List<CardProfileEntity> findByTenantIdOrderByIssuedAtDesc(UUID tenantId);

    List<CardProfileEntity> findByTenantIdAndWalletIdOrderByIssuedAtDesc(UUID tenantId, String walletId);
}
