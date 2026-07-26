package zw.gov.mohcc.impilo.vashandi.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A request to have a rostered shift covered or swapped (V009).
 *
 * <p>Keyed on shift and profile ids, never on typed names: two people share a name, and a clinical
 * rota is not somewhere to resolve that ambiguity by guessing. {@code offeredShiftId} is optional
 * because "please cover me" and "swap with mine" are both real requests.</p>
 *
 * <p>Nothing here approves itself. A swap changes who is clinically accountable for a window, so
 * a decision carries the decider, and a request nobody answered stays PENDING and visible rather
 * than lapsing quietly into "not covered".</p>
 */
@Entity
@Table(name = "vsh_shift_swap_request", schema = "vashandi")
@Getter
@Setter
@NoArgsConstructor
public class ShiftSwapRequestEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    private UUID facilityId;

    @Column(nullable = false)
    private UUID requestingShiftId;

    @Column(nullable = false)
    private UUID requestingProfileId;

    @Column(nullable = false)
    private UUID requestedProfileId;

    private UUID offeredShiftId;

    private String reason;

    @Column(nullable = false, length = 24)
    private String status = "PENDING";

    @Column(length = 128)
    private String decidedBy;

    private OffsetDateTime decidedAt;

    private String decisionNote;

    @Column(length = 128)
    private String createdBy;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
