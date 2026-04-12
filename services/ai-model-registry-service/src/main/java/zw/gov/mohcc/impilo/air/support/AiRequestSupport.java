package zw.gov.mohcc.impilo.air.support;

import java.util.UUID;
import zw.gov.mohcc.impilo.companion.context.RequestContext;

public final class AiRequestSupport {

    private AiRequestSupport() {}

    public static UUID tenantUuid(RequestContext ctx) {
        return UUID.fromString(ctx.tenantId());
    }

    public static UUID correlationUuid(RequestContext ctx) {
        try {
            return UUID.fromString(ctx.correlationId());
        } catch (Exception e) {
            return UUID.randomUUID();
        }
    }
}
