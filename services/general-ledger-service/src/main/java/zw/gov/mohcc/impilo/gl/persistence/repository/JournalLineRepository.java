package zw.gov.mohcc.impilo.gl.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.gl.persistence.entity.JournalLineEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface JournalLineRepository extends JpaRepository<JournalLineEntity, UUID> {

    @Query("""
            select jl.accountId as accountId,
                   coalesce(sum(jl.debitAmount), 0) as totalDebit,
                   coalesce(sum(jl.creditAmount), 0) as totalCredit
            from JournalLineEntity jl
            join jl.journalEntry je
            where je.tenantId = :tenantId
              and je.fiscalPeriodId = :periodId
              and je.status = 'POSTED'
            group by jl.accountId
            """)
    List<AccountAggregate> aggregateByAccount(@Param("tenantId") UUID tenantId, @Param("periodId") UUID periodId);

    interface AccountAggregate {
        UUID getAccountId();

        BigDecimal getTotalDebit();

        BigDecimal getTotalCredit();
    }
}
