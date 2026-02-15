package zw.gov.mohcc.impilo.surv.core;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surv.persistence.entity.CaseEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.CaseRepository;

@Service
public class CaseService {

    private final CaseRepository caseRepository;

    public CaseService(CaseRepository caseRepository) {
        this.caseRepository = caseRepository;
    }

    @Transactional(readOnly = true)
    public Page<CaseEntity> listCases(UUID tenantId, CaseStatus status, Pageable pageable) {
        if (status != null) {
            return caseRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        return caseRepository.findByTenantId(tenantId, pageable);
    }
}
