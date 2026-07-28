package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A definition pinned at a moment LATER than case creation, because the rule says so.
 *
 * <p>Most definitions are pinned by the case's release binding at creation. Some are not: a penalty
 * policy is pinned when liability is assessed, a certificate template when the certificate is
 * issued. Naming that moment explicitly is more honest than assuming every rule was fixed on the
 * day the application arrived — and it is append-only for the same reason the binding is.</p>
 */
@Entity
@Table(name = "regulatory_case_config_pin", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class CaseConfigPinEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID bindingId;

    @Column(nullable = false)
    private UUID definitionId;

    @Column(nullable = false)
    private UUID definitionVersionId;

    @Column(nullable = false, length = 64)
    private String pinnedAtMoment;

    @Column(nullable = false)
    private OffsetDateTime pinnedAt;

    @Column(length = 128)
    private String pinnedBy;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (pinnedAt == null) {
            pinnedAt = OffsetDateTime.now();
        }
    }
}
