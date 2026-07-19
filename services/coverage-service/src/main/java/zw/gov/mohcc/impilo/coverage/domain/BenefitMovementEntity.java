package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Benefit movement ledger — idempotent reserve/consume/release keyed by a unique
 * (accumulator_id, reference). Mirrors the subsidy drawdown ledger's H2 guarantee.
 */
@Entity
@Table(name = "cv_benefit_movements")
public class BenefitMovementEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "accumulator_id", nullable = false)
    private UUID accumulatorId;

    @Column(name = "movement_type", nullable = false, length = 16)
    private String movementType;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "reference", nullable = false, length = 128)
    private String reference;

    @Column(name = "used_after", nullable = false)
    private BigDecimal usedAfter;

    @Column(name = "reserved_after", nullable = false)
    private BigDecimal reservedAfter;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public BenefitMovementEntity() {}

    public static BenefitMovementEntity create(UUID tenantId, UUID accumulatorId, String movementType,
                                               BigDecimal amount, String reference,
                                               BigDecimal usedAfter, BigDecimal reservedAfter) {
        BenefitMovementEntity m = new BenefitMovementEntity();
        m.id = UUID.randomUUID();
        m.tenantId = tenantId;
        m.accumulatorId = accumulatorId;
        m.movementType = movementType;
        m.amount = amount;
        m.reference = reference;
        m.usedAfter = usedAfter;
        m.reservedAfter = reservedAfter;
        return m;
    }

    public UUID getId() { return id; }
    public UUID getAccumulatorId() { return accumulatorId; }
    public String getMovementType() { return movementType; }
    public BigDecimal getAmount() { return amount; }
    public String getReference() { return reference; }
    public BigDecimal getUsedAfter() { return usedAfter; }
    public BigDecimal getReservedAfter() { return reservedAfter; }
}
