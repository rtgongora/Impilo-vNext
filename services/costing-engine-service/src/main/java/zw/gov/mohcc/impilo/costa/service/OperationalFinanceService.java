package zw.gov.mohcc.impilo.costa.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BillHeaderEntity;

/**
 * Hooks operational finance (revenue + AR) into core COSTA billing lifecycle.
 */
@Service
public class OperationalFinanceService {

    private static final Logger log = LoggerFactory.getLogger(OperationalFinanceService.class);

    private final RevenueService revenueService;
    private final ReceivablesService receivablesService;

    public OperationalFinanceService(RevenueService revenueService, ReceivablesService receivablesService) {
        this.revenueService = revenueService;
        this.receivablesService = receivablesService;
    }

    @Transactional
    public void onBillFinalized(BillHeaderEntity bill) {
        try {
            revenueService.recordFromFinalizedBill(bill);
            receivablesService.createFromFinalizedBill(bill);
        } catch (Exception e) {
            log.error("Operational finance post-finalize failed for bill {}", bill.getBillId(), e);
            throw e;
        }
    }
}
