package zw.gov.mohcc.impilo.connectorfhir.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DestinationResponse(UUID destinationId, UUID tenantId, String name, String endpointUrl,
                                    String resourceTypes, String authType, boolean enabled,
                                    OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
