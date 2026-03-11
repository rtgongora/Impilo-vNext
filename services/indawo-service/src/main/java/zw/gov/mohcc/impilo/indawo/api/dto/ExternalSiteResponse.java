package zw.gov.mohcc.impilo.indawo.api.dto;

import java.util.UUID;

public record ExternalSiteResponse(
        UUID siteId,
        String name,
        String type,
        String status
) {}
