package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.HomeStockSource;
import zw.gov.mohcc.impilo.inventory.domain.HomeStockStatus;
import zw.gov.mohcc.impilo.inventory.domain.RefillStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ClientHomeStockEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RefillRequestEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ClientHomeStockRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.RefillRequestRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of the Dura household stock service.
 */
@Service
public class HouseholdStockServiceImpl implements HouseholdStockService {

    private static final Logger log = LoggerFactory.getLogger(HouseholdStockServiceImpl.class);

    private static final Set<RefillStatus> TERMINAL =
            EnumSet.of(RefillStatus.FULFILLED, RefillStatus.REJECTED, RefillStatus.CANCELLED);

    private final ClientHomeStockRepository homeStockRepository;
    private final RefillRequestRepository refillRepository;

    public HouseholdStockServiceImpl(ClientHomeStockRepository homeStockRepository,
                                     RefillRequestRepository refillRepository) {
        this.homeStockRepository = homeStockRepository;
        this.refillRepository = refillRepository;
    }

    @Override
    @Transactional
    public ClientHomeStockEntity addHomeStock(String clientId, String itemCode, String itemName, int qty,
                                              String uom, String batchNumber, LocalDate expiryDate,
                                              HomeStockSource source, String managedByCaregiver, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        requireClient(clientId);
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("itemName is required");
        }
        if (qty < 0) {
            throw new IllegalArgumentException("qty must not be negative");
        }

        ClientHomeStockEntity item = new ClientHomeStockEntity();
        item.setTenantId(ctx.tenantId());
        item.setClientId(clientId);
        item.setItemCode(itemCode);
        item.setItemName(itemName);
        item.setQty(qty);
        item.setUom(uom);
        item.setBatchNumber(batchNumber);
        item.setExpiryDate(expiryDate);
        item.setSource(source != null ? source : HomeStockSource.SELF);
        item.setManagedByCaregiver(managedByCaregiver);
        item.setStatus(qty == 0 ? HomeStockStatus.DEPLETED : HomeStockStatus.ACTIVE);
        item.setNotes(notes);

        item = homeStockRepository.save(item);
        log.info("Home stock added: client={}, item={}, qty={}", clientId, itemName, qty);
        return item;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientHomeStockEntity> listHomeStock(String clientId) {
        TrustContext ctx = TrustContextHolder.require();
        requireClient(clientId);
        return homeStockRepository.findByTenantIdAndClientIdOrderByItemName(ctx.tenantId(), clientId);
    }

    @Override
    @Transactional
    public ClientHomeStockEntity updateQuantity(UUID homeStockId, int newQty) {
        if (newQty < 0) {
            throw new IllegalArgumentException("qty must not be negative");
        }
        ClientHomeStockEntity item = loadOwnedHomeStock(homeStockId);
        item.setQty(newQty);
        if (newQty == 0 && item.getStatus() == HomeStockStatus.ACTIVE) {
            item.setStatus(HomeStockStatus.DEPLETED);
        } else if (newQty > 0 && item.getStatus() == HomeStockStatus.DEPLETED) {
            item.setStatus(HomeStockStatus.ACTIVE);
        }
        item = homeStockRepository.save(item);
        return item;
    }

    @Override
    @Transactional
    public ClientHomeStockEntity setHomeStockStatus(UUID homeStockId, HomeStockStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        ClientHomeStockEntity item = loadOwnedHomeStock(homeStockId);
        item.setStatus(status);
        return homeStockRepository.save(item);
    }

    @Override
    @Transactional
    public RefillRequestEntity requestRefill(String clientId, String itemCode, String itemName,
                                             int qtyRequested, UUID preferredFacilityId, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        requireClient(clientId);
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("itemName is required");
        }
        if (qtyRequested <= 0) {
            throw new IllegalArgumentException("qtyRequested must be positive");
        }

        RefillRequestEntity refill = new RefillRequestEntity();
        refill.setTenantId(ctx.tenantId());
        refill.setClientId(clientId);
        refill.setItemCode(itemCode);
        refill.setItemName(itemName);
        refill.setQtyRequested(qtyRequested);
        refill.setStatus(RefillStatus.REQUESTED);
        refill.setPreferredFacilityId(preferredFacilityId);
        refill.setRequestedBy(ctx.actorId());
        refill.setNotes(notes);

        refill = refillRepository.save(refill);
        log.info("Refill requested: client={}, item={}, qty={}", clientId, itemName, qtyRequested);
        return refill;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefillRequestEntity> listRefills(String clientId, RefillStatus status) {
        TrustContext ctx = TrustContextHolder.require();
        requireClient(clientId);
        if (status != null) {
            return refillRepository.findByTenantIdAndClientIdAndStatusOrderByCreatedAtDesc(
                    ctx.tenantId(), clientId, status);
        }
        return refillRepository.findByTenantIdAndClientIdOrderByCreatedAtDesc(ctx.tenantId(), clientId);
    }

    @Override
    @Transactional
    public RefillRequestEntity updateRefillStatus(UUID refillId, RefillStatus status, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        RefillRequestEntity refill = refillRepository.findById(refillId)
                .orElseThrow(() -> new IllegalArgumentException("Refill request not found: " + refillId));
        if (refill.getTenantId() == null || !refill.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Refill request does not belong to the current tenant");
        }
        if (TERMINAL.contains(refill.getStatus())) {
            throw new IllegalStateException("Refill request is already " + refill.getStatus());
        }
        refill.setStatus(status);
        if (notes != null) {
            refill.setNotes(notes);
        }
        if (status == RefillStatus.FULFILLED) {
            refill.setFulfilledAt(OffsetDateTime.now());
        }
        refill = refillRepository.save(refill);
        log.info("Refill {} -> {}", refillId, status);
        return refill;
    }

    private ClientHomeStockEntity loadOwnedHomeStock(UUID homeStockId) {
        TrustContext ctx = TrustContextHolder.require();
        ClientHomeStockEntity item = homeStockRepository.findById(homeStockId)
                .orElseThrow(() -> new IllegalArgumentException("Home stock item not found: " + homeStockId));
        if (item.getTenantId() == null || !item.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Home stock item does not belong to the current tenant");
        }
        return item;
    }

    private void requireClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId is required");
        }
    }
}
