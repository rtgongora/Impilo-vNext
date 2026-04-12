package zw.gov.mohcc.impilo.wallet.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    Page<TransactionEntity> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    Optional<TransactionEntity> findByReference(String reference);

    Optional<TransactionEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionEntity t " +
           "WHERE t.walletId = :walletId AND t.direction = 'DEBIT' " +
           "AND CAST(t.createdAt AS date) = :date")
    BigDecimal sumDebitsByDate(@Param("walletId") UUID walletId, @Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionEntity t " +
           "WHERE t.walletId = :walletId AND t.direction = 'DEBIT' " +
           "AND YEAR(t.createdAt) = :year AND MONTH(t.createdAt) = :month")
    BigDecimal sumDebitsByMonth(@Param("walletId") UUID walletId, @Param("year") int year, @Param("month") int month);
}
