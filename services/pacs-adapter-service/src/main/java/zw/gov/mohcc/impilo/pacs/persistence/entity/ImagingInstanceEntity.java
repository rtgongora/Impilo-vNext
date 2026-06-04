package zw.gov.mohcc.impilo.pacs.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

@Entity
@Table(name = "imaging_instance", schema = "pacs")
public class ImagingInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    private ImagingSeriesEntity series;

    @Column(name = "sop_instance_uid", nullable = false, length = 255)
    private String sopInstanceUid;

    @Column(name = "instance_number")
    private Integer instanceNumber;

    @Column(name = "storage_ref", nullable = false, columnDefinition = "TEXT")
    private String storageRef;

    @Column(name = "transfer_syntax_uid", length = 100)
    private String transferSyntaxUid;

    @Column(name = "frame_count", nullable = false)
    private int frameCount = 1;

    @Column(name = "preview_ref", columnDefinition = "TEXT")
    private String previewRef;

    @Column(name = "orthanc_instance_id", length = 255)
    private String orthancInstanceId;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ImagingSeriesEntity getSeries() {
        return series;
    }

    public void setSeries(ImagingSeriesEntity series) {
        this.series = series;
    }

    public String getSopInstanceUid() {
        return sopInstanceUid;
    }

    public void setSopInstanceUid(String sopInstanceUid) {
        this.sopInstanceUid = sopInstanceUid;
    }

    public Integer getInstanceNumber() {
        return instanceNumber;
    }

    public void setInstanceNumber(Integer instanceNumber) {
        this.instanceNumber = instanceNumber;
    }

    public String getStorageRef() {
        return storageRef;
    }

    public void setStorageRef(String storageRef) {
        this.storageRef = storageRef;
    }

    public String getTransferSyntaxUid() {
        return transferSyntaxUid;
    }

    public void setTransferSyntaxUid(String transferSyntaxUid) {
        this.transferSyntaxUid = transferSyntaxUid;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public void setFrameCount(int frameCount) {
        this.frameCount = frameCount;
    }

    public String getPreviewRef() {
        return previewRef;
    }

    public void setPreviewRef(String previewRef) {
        this.previewRef = previewRef;
    }

    public String getOrthancInstanceId() {
        return orthancInstanceId;
    }

    public void setOrthancInstanceId(String orthancInstanceId) {
        this.orthancInstanceId = orthancInstanceId;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
