package zw.gov.mohcc.impilo.dataingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "din_dead_letter_event")
public class DeadLetterEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "pod_id", length = 64)
    private String podId;

    @Column(name = "topic_name", nullable = false, length = 255)
    private String topicName;

    @Column(name = "partition_num", nullable = false)
    private int partitionNum;

    @Column(name = "offset_num", nullable = false)
    private long offsetNum;

    @Column(name = "key_bytes", columnDefinition = "TEXT")
    private String keyBytes;

    @Column(name = "value_bytes", nullable = false, columnDefinition = "TEXT")
    private String valueBytes;

    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_class", length = 512)
    private String errorClass;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected DeadLetterEventEntity() {}

    public DeadLetterEventEntity(String topicName, int partitionNum, long offsetNum,
                                  String keyBytes, String valueBytes,
                                  String errorMessage, String errorClass) {
        this.topicName = topicName;
        this.partitionNum = partitionNum;
        this.offsetNum = offsetNum;
        this.keyBytes = keyBytes;
        this.valueBytes = valueBytes;
        this.errorMessage = errorMessage;
        this.errorClass = errorClass;
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public String getTopicName() { return topicName; }
    public int getPartitionNum() { return partitionNum; }
    public long getOffsetNum() { return offsetNum; }
    public String getKeyBytes() { return keyBytes; }
    public String getValueBytes() { return valueBytes; }
    public String getErrorMessage() { return errorMessage; }
    public String getErrorClass() { return errorClass; }
    public int getRetryCount() { return retryCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setPodId(String podId) { this.podId = podId; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
}
