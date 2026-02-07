package zw.gov.mohcc.impilo.zibo.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Join entity linking a pack to its constituent artifacts.
 */
@Entity
@Table(name = "zibo_pack_artifacts")
public class PackArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "pack_id", nullable = false)
    private UUID packId;

    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    // Getters and setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPackId() { return packId; }
    public void setPackId(UUID packId) { this.packId = packId; }

    public UUID getArtifactId() { return artifactId; }
    public void setArtifactId(UUID artifactId) { this.artifactId = artifactId; }
}
