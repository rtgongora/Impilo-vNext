package zw.gov.mohcc.impilo.vashandi.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A theatre case-team member in a specific role, with covering-shift validation. */
@Entity
@Table(name = "vsh_theatre_team_member", schema = "vashandi")
@Getter
@Setter
@NoArgsConstructor
public class TheatreTeamMemberEntity {

    public static final String COVERAGE_COVERED = "COVERED";
    public static final String COVERAGE_NO_SHIFT = "NO_SHIFT";
    public static final String COVERAGE_UNVALIDATED = "UNVALIDATED";

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID teamId;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(nullable = false)
    private UUID workforceProfileId;

    private UUID shiftId;

    @Column(nullable = false)
    private boolean confirmed = false;

    @Column(nullable = false, length = 24)
    private String shiftCoverage = COVERAGE_UNVALIDATED;

    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
