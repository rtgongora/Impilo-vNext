package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.BatchLotStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.BatchLotEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ItemEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ItemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of the Dura stock-aware availability service.
 */
@Service
public class StockAvailabilityServiceImpl implements StockAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(StockAvailabilityServiceImpl.class);

    private final ReservationService reservationService;
    private final BatchLotService batchLotService;
    private final ItemRepository itemRepository;

    public StockAvailabilityServiceImpl(ReservationService reservationService,
                                        BatchLotService batchLotService,
                                        ItemRepository itemRepository) {
        this.reservationService = reservationService;
        this.batchLotService = batchLotService;
        this.itemRepository = itemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityResult availability(UUID facilityId, UUID storeId, String itemCode) {
        TrustContext ctx = TrustContextHolder.require();
        if (facilityId == null || storeId == null) {
            throw new IllegalArgumentException("facilityId and storeId are required");
        }
        if (itemCode == null || itemCode.isBlank()) {
            throw new IllegalArgumentException("itemCode is required");
        }

        int available = reservationService.availableQuantity(facilityId, storeId, itemCode);
        int reserved = reservationService.reservedQuantity(facilityId, storeId, itemCode);
        int onHand = available + reserved;
        boolean stockOut = available <= 0;

        LocalDate today = LocalDate.now();
        List<UsableBatch> usableBatches = new ArrayList<>();
        LocalDate nearestExpiry = null;
        for (BatchLotEntity b : batchLotService.listBatchesForItem(itemCode)) {
            boolean usable = b.getStatus() == BatchLotStatus.ACTIVE
                    && (b.getExpiryDate() == null || !b.getExpiryDate().isBefore(today));
            if (usable) {
                usableBatches.add(new UsableBatch(b.getBatchLotId(), b.getBatchNumber(), b.getExpiryDate()));
                if (b.getExpiryDate() != null && (nearestExpiry == null || b.getExpiryDate().isBefore(nearestExpiry))) {
                    nearestExpiry = b.getExpiryDate();
                }
            }
        }

        List<SubstituteOption> substitutes = stockOut
                ? findSubstitutes(ctx.tenantId(), facilityId, storeId, itemCode)
                : List.of();

        log.debug("Availability: itemCode={}, available={}, stockOut={}, substitutes={}",
                itemCode, available, stockOut, substitutes.size());
        return new AvailabilityResult(itemCode, available, onHand, reserved, stockOut,
                usableBatches, nearestExpiry, substitutes);
    }

    /**
     * Approved substitutes: other active commodities sharing the requested item's
     * substitution group, that currently have available stock at the store.
     */
    private List<SubstituteOption> findSubstitutes(UUID tenantId, UUID facilityId, UUID storeId, String itemCode) {
        ItemEntity item = itemRepository.findByTenantIdAndItemCode(tenantId, itemCode).orElse(null);
        if (item == null || item.getSubstitutionGroup() == null || item.getSubstitutionGroup().isBlank()) {
            return List.of();
        }
        List<SubstituteOption> options = new ArrayList<>();
        for (ItemEntity candidate : itemRepository.findByTenantIdAndSubstitutionGroup(tenantId, item.getSubstitutionGroup())) {
            if (candidate.getItemCode().equals(itemCode) || !candidate.isActive()) {
                continue;
            }
            int candidateAvailable = reservationService.availableQuantity(facilityId, storeId, candidate.getItemCode());
            if (candidateAvailable > 0) {
                options.add(new SubstituteOption(candidate.getItemCode(), candidate.getName(), candidateAvailable));
            }
        }
        return options;
    }
}
