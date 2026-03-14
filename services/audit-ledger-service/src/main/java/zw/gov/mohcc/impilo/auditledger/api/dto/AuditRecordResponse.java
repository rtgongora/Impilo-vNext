package zw.gov.mohcc.impilo.auditledger.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditRecordResponse(UUID recordId, UUID tenantId, long sequenceNum, UUID correlationId,
                                    String actorId, String actorType, String action, String resourceType,
                                    String resourceId, String outcome, Object detail,
                                    OffsetDateTime occurredAt, String prevHash, String entryHash) {}
