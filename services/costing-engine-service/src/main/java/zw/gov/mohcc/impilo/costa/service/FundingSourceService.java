package zw.gov.mohcc.impilo.costa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.FundingSourceEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.FundingSourceType;
import zw.gov.mohcc.impilo.costa.domain.repository.FundingSourceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Funding sources / donors / grants that pay for budget lines. */
@Service
public class FundingSourceService {

    private final FundingSourceRepository repository;

    public FundingSourceService(FundingSourceRepository repository) {
        this.repository = repository;
    }

    public record NewFundingSource(String name, String code, String sourceType,
                                   String donorName, String grantReference,
                                   BigDecimal ceilingAmount, LocalDate startDate, LocalDate endDate) {}

    @Transactional
    public FundingSourceEntity create(UUID tenantId, NewFundingSource cmd) {
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("Funding source name is required");
        }
        if (cmd.code() == null || cmd.code().isBlank()) {
            throw new IllegalArgumentException("Funding source code is required");
        }
        // validate enum
        FundingSourceType.valueOf(cmd.sourceType());
        if (repository.existsByTenantIdAndCode(tenantId, cmd.code())) {
            throw new IllegalArgumentException("Funding source code already exists: " + cmd.code());
        }
        FundingSourceEntity e = new FundingSourceEntity();
        e.setTenantId(tenantId);
        e.setName(cmd.name());
        e.setCode(cmd.code());
        e.setSourceType(cmd.sourceType());
        e.setDonorName(cmd.donorName());
        e.setGrantReference(cmd.grantReference());
        e.setCeilingAmount(cmd.ceilingAmount());
        e.setAbsorbedAmount(BigDecimal.ZERO);
        e.setStartDate(cmd.startDate());
        e.setEndDate(cmd.endDate());
        e.setStatus("ACTIVE");
        return repository.save(e);
    }

    public List<FundingSourceEntity> list(UUID tenantId) {
        return repository.findByTenantIdOrderByNameAsc(tenantId);
    }

    public FundingSourceEntity get(UUID tenantId, UUID fundingSourceId) {
        return repository.findByFundingSourceIdAndTenantId(fundingSourceId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Funding source not found"));
    }
}
