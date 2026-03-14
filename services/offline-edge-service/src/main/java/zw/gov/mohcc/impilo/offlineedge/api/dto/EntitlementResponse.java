package zw.gov.mohcc.impilo.offlineedge.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EntitlementResponse(UUID entitlementId, UUID tenantId, String actorId, String facilityRef,
                                    String workflowType, String tokenHash, OffsetDateTime issuedAt,
                                    OffsetDateTime expiresAt, boolean revoked) {}
