package zw.gov.mohcc.impilo.vashandi.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A theatre case team, referencing a scheduling session. */
@Entity
@Table(name = "vsh_theatre_team", schema = "vashandi")
@Getter
@Setter
@NoArgsConstructor
public class TheatreTeamEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_STOOD_DOWN = "STOOD_DOWN";

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID sessionRef;

    private UUID orListItemRef;

    @Column(nullable = false, length = 24)
    private String status = STATUS_DRAFT;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
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
