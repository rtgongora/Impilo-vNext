package zw.gov.mohcc.impilo.madi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.madi.domain.BloodUnitStatus;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.integration.InventoryIntegration;
import zw.gov.mohcc.impilo.madi.persistence.entity.*;
import zw.gov.mohcc.impilo.madi.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BloodBankService {

    private final BloodBankRepository bloodBankRepository;
    private final BloodInventoryBalanceRepository balanceRepository;
    private final BloodStockMovementRepository movementRepository;
    private final BloodStockReconciliationRepository reconciliationRepository;
    private final BloodWastageRepository wastageRepository;
    private final BloodReturnRepository returnRepository;
    private final BloodUnitService bloodUnitService;
    private final InventoryIntegration inventoryIntegration;
    private final MadiEventEmitter eventEmitter;

    public BloodBankService(BloodBankRepository bloodBankRepository,
                            BloodInventoryBalanceRepository balanceRepository,
                            BloodStockMovementRepository movementRepository,
                            BloodStockReconciliationRepository reconciliationRepository,
                            BloodWastageRepository wastageRepository,
                            BloodReturnRepository returnRepository,
                            BloodUnitService bloodUnitService,
                            InventoryIntegration inventoryIntegration,
                            MadiEventEmitter eventEmitter) {
        this.bloodBankRepository = bloodBankRepository;
        this.balanceRepository = balanceRepository;
        this.movementRepository = movementRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.wastageRepository = wastageRepository;
        this.returnRepository = returnRepository;
        this.bloodUnitService = bloodUnitService;
        this.inventoryIntegration = inventoryIntegration;
        this.eventEmitter = eventEmitter;
    }

    @Transactional
    public BloodBankEntity registerBank(BloodBankEntity bank) {
        bank.setUpdatedAt(OffsetDateTime.now());
        return bloodBankRepository.save(bank);
    }

    @Transactional(readOnly = true)
    public List<BloodInventoryBalanceEntity> stock(UUID tenantId, UUID bloodBankId) {
        return balanceRepository.findByTenantIdAndBloodBankId(tenantId, bloodBankId);
    }

    @Transactional
    public BloodStockMovementEntity recordMovement(UUID tenantId, UUID bloodBankId, UUID unitId,
                                                   String inventoryItemCode, String movementType,
                                                   int quantity, String referenceType, String referenceId,
                                                   String movedBy, UUID facilityId) {
        BloodStockMovementEntity movement = new BloodStockMovementEntity();
        movement.setTenantId(tenantId);
        movement.setBloodBankId(bloodBankId);
        movement.setUnitId(unitId);
        movement.setInventoryItemCode(inventoryItemCode);
        movement.setMovementType(movementType);
        movement.setQuantity(quantity);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setMovedBy(movedBy);
        movement.setFacilityId(facilityId);
        movement.setStatus("POSTED");
        movement.setMovedAt(OffsetDateTime.now());
        BloodStockMovementEntity saved = movementRepository.save(movement);
        adjustBalance(tenantId, bloodBankId, inventoryItemCode, movementType, quantity, facilityId);
        inventoryIntegration.notifyStockChange(inventoryItemCode, movementType, quantity);
        eventEmitter.emit("BLOOD_BANK", bloodBankId.toString(), "STOCK_MOVEMENT", "BLOOD_BANK",
                bloodBankId.toString(),
                Map.of("inventoryItemCode", inventoryItemCode, "movementType", movementType), tenantId);
        return saved;
    }

    @Transactional
    public BloodStockReconciliationEntity reconcile(UUID tenantId, UUID bloodBankId,
                                                    int varianceCount, String notes, String reconciledBy,
                                                    UUID facilityId) {
        BloodStockReconciliationEntity rec = new BloodStockReconciliationEntity();
        rec.setTenantId(tenantId);
        rec.setBloodBankId(bloodBankId);
        rec.setVarianceCount(varianceCount);
        rec.setNotes(notes);
        rec.setReconciledBy(reconciledBy);
        rec.setFacilityId(facilityId);
        rec.setStatus("COMPLETED");
        rec.setReconciledAt(OffsetDateTime.now());
        return reconciliationRepository.save(rec);
    }

    @Transactional
    public BloodWastageEntity recordWastage(UUID tenantId, UUID unitId, UUID bloodBankId,
                                            String reasonCode, String recordedBy, UUID facilityId) {
        bloodUnitService.transition(tenantId, unitId, BloodUnitStatus.DISCARDED);
        BloodWastageEntity wastage = new BloodWastageEntity();
        wastage.setTenantId(tenantId);
        wastage.setUnitId(unitId);
        wastage.setBloodBankId(bloodBankId);
        wastage.setReasonCode(reasonCode);
        wastage.setRecordedBy(recordedBy);
        wastage.setFacilityId(facilityId);
        wastage.setStatus("RECORDED");
        wastage.setRecordedAt(OffsetDateTime.now());
        BloodWastageEntity saved = wastageRepository.save(wastage);
        eventEmitter.emit("BLOOD_BANK", bloodBankId.toString(), "BLOOD_WASTAGE", "BLOOD_UNIT",
                unitId.toString(), Map.of("reasonCode", reasonCode), tenantId);
        return saved;
    }

    @Transactional
    public BloodReturnEntity recordReturn(UUID tenantId, UUID unitId, UUID bloodBankId,
                                          String reasonCode, String returnedBy, UUID facilityId) {
        bloodUnitService.transition(tenantId, unitId, BloodUnitStatus.AVAILABLE);
        BloodReturnEntity ret = new BloodReturnEntity();
        ret.setTenantId(tenantId);
        ret.setUnitId(unitId);
        ret.setBloodBankId(bloodBankId);
        ret.setReasonCode(reasonCode);
        ret.setReturnedBy(returnedBy);
        ret.setFacilityId(facilityId);
        ret.setStatus("RETURNED");
        ret.setReturnedAt(OffsetDateTime.now());
        return returnRepository.save(ret);
    }

    private void adjustBalance(UUID tenantId, UUID bloodBankId, String inventoryItemCode,
                               String movementType, int quantity, UUID facilityId) {
        BloodInventoryBalanceEntity balance = balanceRepository
                .findByTenantIdAndBloodBankIdAndInventoryItemCodeAndBloodGroupAndComponentType(
                        tenantId, bloodBankId, inventoryItemCode, "UNKNOWN", "WHOLE_BLOOD")
                .orElseGet(() -> {
                    BloodInventoryBalanceEntity b = new BloodInventoryBalanceEntity();
                    b.setTenantId(tenantId);
                    b.setBloodBankId(bloodBankId);
                    b.setInventoryItemCode(inventoryItemCode);
                    b.setBloodGroup("UNKNOWN");
                    b.setComponentType("WHOLE_BLOOD");
                    b.setQuantityOnHand(0);
                    b.setQuantityReserved(0);
                    b.setFacilityId(facilityId);
                    b.setStatus("ACTIVE");
                    return b;
                });
        int delta = "ISSUE".equals(movementType) || "WASTAGE".equals(movementType) ? -quantity : quantity;
        balance.setQuantityOnHand(Math.max(0, balance.getQuantityOnHand() + delta));
        balance.setUpdatedAt(OffsetDateTime.now());
        balanceRepository.save(balance);
    }
}
